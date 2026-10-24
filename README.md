# Shieldy for Magisk (DNSCrypt Magisk Module)

🇬🇧 **English** · [🇷🇺 Русский](README.ru.md)

[![Support the project](https://img.shields.io/badge/Support-DonationAlerts-F57D07)](https://www.donationalerts.com/r/skofqq)

Support development: [donationalerts.com/r/skofqq](https://www.donationalerts.com/r/skofqq)

A Magisk module that routes all Android DNS traffic to a local
[dnscrypt-proxy](https://github.com/DNSCrypt/dnscrypt-proxy) and sends queries
upstream encrypted: DNSCrypt v2, DNS-over-HTTPS, Anonymized DNSCrypt, ODoH.

Interception is done at the netfilter level, so the proxy also receives queries
from apps that talk to their own resolvers on port 53, bypassing the system DNS settings.

Package contents:

| Component | Purpose |
|---|---|
| Magisk module | installs dnscrypt-proxy 2.1.18, DNS interception, process supervisor, rule recovery after netd |
| `dnscrypt-ctl` | command-line control: status, settings, server selection and exclusion, logs, diagnostics |
| Shieldy | Android app with a graphical interface on top of `dnscrypt-ctl`, the APK is published in [releases](https://github.com/skofqq/DNSCrypt-Android-Magisk/releases/latest) |

## Requirements

| | |
|---|---|
| Root | Magisk 20.4 or newer. Tested on Magisk 30.x and 31.x. KernelSU and APatch have not been tested |
| Android | tested on Android 15–16 |
| Architecture | `arm64-v8a`, `armeabi-v7a`. The installer picks the binary by the device `$ARCH` |
| Kernel | `iptables` with the `nat` table. `ip6tables nat` is recommended but not required (see [IPv6](#ipv6)) |
| App | Android 12 or newer, root access |

One zip covers both architectures.

## Installation

1. Download `dnscrypt-proxy-android-<version>.zip` from the [releases page](https://github.com/skofqq/DNSCrypt-Android-Magisk/releases/latest).
2. Magisk → Modules → Install from storage → select the zip.
3. Reboot the device.
4. Verify: `su -c dnscrypt-ctl test`.

On update the existing `dnscrypt-proxy.toml` is kept; the new config is placed next to it
as `dnscrypt-proxy.toml.new`, and the previous one is copied to `.bak`.
Module updates are delivered through Magisk (`updateJson` in `module.prop`).

### Building from source

```sh
./build.sh          # arm64 and arm: arm64 from the repository, arm downloaded from the dnscrypt-proxy GitHub releases
./build.sh --arm64  # arm64 only
```

Packing uses `zip`; if it is missing, Python `zipfile`, `7z` or `jar` from a JDK is used.
CI ([.github/workflows/build.yml](.github/workflows/build.yml)) runs `shellcheck` on the
scripts, builds the zip, checks that the arm64 and arm binaries are present, and attaches
the zip to the release when a tag is pushed.

## Files and directories

| Path | Contents |
|---|---|
| `/data/adb/dnscrypt-proxy/dnscrypt-proxy.toml` | dnscrypt-proxy config |
| `/data/adb/dnscrypt-proxy/module.conf` | module settings |
| `/data/adb/dnscrypt-proxy/*.txt` | `blocked-names`, `blocked-ips`, `allowed-names`, `allowed-ips`, `exclude-ips` |
| `/data/adb/dnscrypt-proxy/public-resolvers.md` | resolver list cache, downloaded and verified with minisign by dnscrypt-proxy itself |
| `/system/bin/dnscrypt-proxy`, `/system/bin/dnscrypt-ctl` | binaries, mounted by Magisk |

The directory has mode `0700`. Nothing is stored in internal storage (`/storage/emulated/0`),
so the config is available before the first unlock after boot (FBE).

## Module settings

`/data/adb/dnscrypt-proxy/module.conf`. Change with `dnscrypt-ctl set <key> <value>`,
then `dnscrypt-ctl restart`. Reset all settings: `dnscrypt-ctl reset`.

| Key | Values | Default | Purpose |
|---|---|---|---|
| `autostart` | `true` / `false` | `true` | start at boot. `false`: the module stays installed but nothing starts at boot |
| `redirect_ipv4` | `true` / `false` | `true` | intercept IPv4 DNS (port 53) |
| `redirect_ipv6` | `auto` / `redirect` / `off` | `auto` | intercept IPv6 DNS, see [IPv6](#ipv6) |
| `tether_redirect` | `true` / `false` | `false` | intercept DNS of hotspot and USB tethering clients (`PREROUTING`) |
| `disable_private_dns` | `true` / `false` | `true` | turn off system Private DNS (DoT on port 853 cannot be intercepted). The original mode is restored on `stop` and on module removal |
| `restart_delay_max` | 5…600 | `60` | maximum pause between restarts of a crashed proxy, seconds |
| `run_gid` | empty, group name or GID | empty | primary group of the dnscrypt-proxy process. Experimental, needed only when chained with box_for_magisk |

Values are validated by `set`; invalid ones are rejected.

The default dnscrypt-proxy config listens on `127.0.0.1:5354` and `[::1]:5354`.
`server_names` is empty, so servers are selected automatically by the
`require_dnssec`, `require_nolog`, `require_nofilter` filters (all `true`) with the `p2` load-balancing strategy.

## Control: dnscrypt-ctl

Requires root (`su -c dnscrypt-ctl …`).

```
dnscrypt-ctl status            # process state, port, IPv4/IPv6 rules, Private DNS
dnscrypt-ctl json              # the same as JSON (used by the app)
dnscrypt-ctl start | stop | restart
dnscrypt-ctl test              # resolves a unique name via the system resolver and checks the DNAT counters
dnscrypt-ctl rules             # nat/DNSCRYPT chains for IPv4 and IPv6
dnscrypt-ctl conflicts         # other port 53 interceptors, processes on the proxy port
dnscrypt-ctl log [N]           # module log
dnscrypt-ctl proxylog [N]      # dnscrypt-proxy log
dnscrypt-ctl set <key> <value>
dnscrypt-ctl reset             # module.conf back to defaults, with a backup

dnscrypt-ctl servers info      # resolver cache, count, current selection
dnscrypt-ctl servers list      # server names from the cache
dnscrypt-ctl servers relays    # Anonymized DNSCrypt relays
dnscrypt-ctl servers get       # server_names or auto
dnscrypt-ctl servers set a,b   # set server_names
dnscrypt-ctl servers add|remove <name>
dnscrypt-ctl servers auto      # clear server_names, select by the require_* filters
dnscrypt-ctl servers active    # servers currently selected by the proxy
dnscrypt-ctl servers live      # JSON: strategy, number of live servers, RTT of each
dnscrypt-ctl servers excluded [list]   # disabled_server_names
dnscrypt-ctl servers exclude|include a,b
```

`stop` persists across reboots: a manually stopped proxy does not start at boot or on
`restart` until the next `start`. A backup is made before every write to
`dnscrypt-proxy.toml`. Config changes take effect after `restart`.

## How it works

* `post-fs-data.sh` prepares `/data/adb/dnscrypt-proxy`. The network is not touched at this stage.
* `service.sh` starts `scripts/supervisor.sh` if `autostart=true` and there is no manual-stop flag.
* The supervisor:
  * waits for iptables to become available;
  * enables `route_localnet`;
  * builds the `nat/DNSCRYPT` chain: exclusions, then DNAT `udp/tcp :53` to `127.0.0.1:5354`;
  * hooks the chain into `OUTPUT` and starts dnscrypt-proxy;
  * restarts a crashed proxy with an exponential backoff (5 s … `restart_delay_max`);
  * checks every 30 s that the rules are in place and restores them if netd removed them.
* Addresses from `bootstrap_resolvers`, `netprobe_address` and `exclude-ips.txt` are excluded
  from interception, otherwise the proxy would intercept its own queries. An app that sends DNS
  directly to these addresses (9.9.9.9, 1.1.1.1, 8.8.8.8 by default) bypasses the proxy.
  If this matters, set `bootstrap_resolvers` to addresses that apps do not use.
* Private DNS is turned off after `sys.boot_completed`; the original mode is saved.
* Wi-Fi hotspot is not affected: IPv6 is not disabled globally, `FORWARD` is not modified,
  `PREROUTING` is used only with `tether_redirect=true`.

### IPv6

| Kernel | `auto` | `redirect` | `off` |
|---|---|---|---|
| `ip6tables nat` available | DNAT to `[::1]:5354` | DNAT to `[::1]:5354` | IPv6 DNS bypasses the proxy |
| no `ip6tables nat` | REJECT DNS to IPv6 addresses in `filter/OUTPUT` while an IPv4 route exists | REJECT always | IPv6 DNS bypasses the proxy |

After a REJECT the Android resolver falls back to IPv4 DNS, which is intercepted.
On an IPv6-only network `auto` lifts the block so DNS does not disappear entirely.
Current state: the `IPv6 DNS` line in `status`, the `ipv6_dns` field in `json`
(`redirected` / `blocked` / `bypass`).

## Compatibility with other modules

`dnscrypt-ctl conflicts` shows the actual state on the device.

### box_for_magisk

[box_for_magisk](https://github.com/taamarin/box_for_magisk) also intercepts DNS:
with the clash core via `nat/CLASH_DNS_LOCAL` (port 1053), with other cores via TPROXY in `mangle`.
Two port 53 interceptors cannot work at the same time. Two setups are supported.

**Switching.** Both modules are enabled in Magisk, one of them is active. Switching is done
with the Start/Stop buttons in the apps (Shieldy and Boxy) or with `dnscrypt-ctl stop` / `start`.
Stopping dnscrypt-proxy removes the rules, restores Private DNS and persists across reboots.
Box in tproxy mode does not take port 53 (`BOX_LOCAL` returns it); this is recognized by
`conflicts` and the `box_dns` field.

Box's own dnscrypt-proxy may also listen on port 5354. The module tracks its own process
by the binary path, not by name, so the processes are not confused.
A busy port is shown in `status` and `conflicts`.

**Chain.** Box intercepts DNS, dnscrypt-proxy works as its upstream resolver:

```sh
dnscrypt-ctl set redirect_ipv4 false
dnscrypt-ctl set redirect_ipv6 off
dnscrypt-ctl set run_gid net_admin
dnscrypt-ctl restart
```

Block for `/data/adb/box/clash/config.yaml`:

```yaml
dns:
  enable: true
  listen: 0.0.0.0:1053
  ipv6: false
  enhanced-mode: fake-ip      # redir-host if all DNS must go through dnscrypt-proxy
  fake-ip-range: 198.18.0.1/16
  fake-ip-filter: ['*.lan', '*.local', '+.pool.ntp.org', 'time.*.com']
  default-nameserver: [127.0.0.1:5354]
  nameserver: [127.0.0.1:5354]
  proxy-server-nameserver: [127.0.0.1:5354]
  fallback: []
```

* `fallback` must be empty, otherwise some queries go out directly.
* `proxy-server-nameserver` points to dnscrypt-proxy, otherwise proxy server addresses are
  resolved by the system DNS and the query loops back into clash.
* `run_gid=net_admin` takes dnscrypt-proxy's upstream queries out of box interception:
  box lets through traffic owned by `root:net_admin`.
* With `fake-ip`, direct domains and proxy server addresses go through dnscrypt-proxy.
* With `redir-host`, all DNS goes through dnscrypt-proxy at the cost of first-connection latency.

### Boxy

[Boxy](https://github.com/skofqq/Boxy) is an app for managing Box for Root
(`/data/adb/box`) with its own build of the module. Compatibility was verified on a device
with both modules enabled in Magisk.

* **Switching.** DNS interception is turned on with Start in one app after Stop in the other:
  Stop in Boxy and Start in Shieldy, or the other way round. A stop in Shieldy persists across reboots.
  To keep Box from starting after a reboot, turn off "Start at boot" in Boxy
  (file `/data/adb/box/manual`).
* **Boxy's built-in DNSCrypt** (Tools → DNS) runs a separate dnscrypt-proxy from
  `/data/adb/box/bin` with the config in `/data/adb/box/dnscrypt`, as the core's upstream DNS.
  It may listen on the same port 5354 as this module. Only one of them should run at a time;
  a busy port is shown by `dnscrypt-ctl status` and `conflicts`.
  The processes are not confused: the module tracks its own dnscrypt-proxy by the binary path.
* **Chain** (Box intercepts DNS, this module encrypts) is set up the same way as for
  box_for_magisk above, with Boxy's built-in DNSCrypt turned off.
* In tproxy mode without DNS interception Box does not take port 53 (`BOX_LOCAL` returns it),
  and `conflicts` does not treat it as a competitor. If DNS hijack is enabled in Boxy,
  `conflicts` and the `box_dns` field in `json` show that Box takes DNS.

### magisk-zapret2

[magisk-zapret2](https://git.zapret.moe/zapretdiscordyoutube/magisk-zapret2) is compatible.
It works in the `mangle` table (`ZAPRET2_OUT` / `ZAPRET2_IN`, `NFQUEUE`) and its presets
do not filter port 53. If a DNSCrypt server listens on a UDP port in the `443-65535` range,
proxy packets pass through NFQUEUE without desync: `--dpi-desync-any-protocol`
is not enabled in the presets.

## Shieldy (formerly DNSCrypt Manager)

Shieldy is published as `Shieldy-<version>.apk` in the releases (package `com.skofqq.shieldy`).
It controls the module through `dnscrypt-ctl` and does not install the module itself.

On first launch Shieldy migrates settings from DNSCrypt Manager (`io.github.skofqq.dnscryptmanager`)
if it is installed; after that the old app can be removed. Magisk asks for root for the new package.

* **Status:**
  * proxy state and uptime, Start/Stop buttons;
  * DNS check: which resolvers an external service sees, whether there is a leak;
  * current server and RTT;
  * module version and update check;
  * warnings when interception rules are missing, IPv6 leaks or Private DNS is on.
* **Servers:**
  * selection from `public-resolvers.md` with search and filters by protocol, DNSSEC, logging and filtering;
  * server exclusion by country. The country is detected from the server description and a GeoIP database; for undetected servers it can be set manually.
* **Blocking:**
  * blocklists (HaGeZi, OISD, custom URL) with daily updates;
  * block and allow domains from the log.
* **Settings:**
  * all `module.conf` settings, applied with a restart only if the proxy is running;
  * `dnscrypt-proxy.toml` editor with backups;
  * module update from the release.
* **Logs:** module and proxy logs, rules, conflicts, Live mode.
* **Interface:** 12 languages (en, ru, uk, be, kk, es, fr, it, pt, de, pl, tr), UI scale 80–120%, Quick Settings tile.

## Changes from the original module

The module is based on [d3cim/dnscrypt-proxy-android](https://github.com/d3cim/dnscrypt-proxy-android),
abandoned in 2024. The scripts have been rewritten:

| Problem in the original module | Solution |
|---|---|
| a crashed proxy was restarted without pauses and drained the battery | supervisor with exponential backoff and a log |
| config on `/storage/emulated/0` unavailable before unlock (FBE) | everything is stored in `/data/adb/dnscrypt-proxy` |
| global IPv6 disabling broke Wi-Fi hotspot ([#7](https://github.com/d3cim/dnscrypt-proxy-android/issues/7)) | IPv6 is not disabled, rules are set in `late_start` and restored after netd |
| files left behind after removal ([#1](https://github.com/d3cim/dnscrypt-proxy-android/issues/1)) | removal deletes the rules, stops the process, restores Private DNS |
| a single hard-coded IP was excluded from interception | exclusions from `bootstrap_resolvers`, `netprobe_address`, `exclude-ips.txt` |
| DNAT to 127.0.0.1 did not work without `route_localnet` | `route_localnet=1`, restored on removal |
| duplicate rules on every start | dedicated `nat/DNSCRYPT` chain, `-C` check before insertion |
| dnscrypt-proxy 2.1.5 (2023) | 2.1.18, including PQDNSCrypt |

Version history: [CHANGELOG.md](CHANGELOG.md) (in Russian).

## Acknowledgements

* [Frank Denis (jedisct1)](https://github.com/jedisct1) and the [DNSCrypt/dnscrypt-proxy](https://github.com/DNSCrypt/dnscrypt-proxy) contributors: the proxy, the DNSCrypt v2 protocol, Anonymized DNSCrypt.
* [DNSCrypt](https://github.com/DNSCrypt): the `public-resolvers.md` resolver list and its minisign signatures.
* [d3cim/dnscrypt-proxy-android](https://github.com/d3cim/dnscrypt-proxy-android): the original Magisk module structure.
* [topjohnwu](https://github.com/topjohnwu): [Magisk](https://github.com/topjohnwu/Magisk) and [libsu](https://github.com/topjohnwu/libsu).
* [taamarin/box_for_magisk](https://github.com/taamarin/box_for_magisk), [Boxy](https://github.com/skofqq/Boxy) and [magisk-zapret2](https://git.zapret.moe/zapretdiscordyoutube/magisk-zapret2): sources used to verify compatibility.
* [DB-IP](https://db-ip.com): IP to Country Lite database (CC BY 4.0), used by the app.

## License

[GNU GPL v3](LICENSE.md). Files in `config/` are distributed under the [dnscrypt-proxy license](config/LICENSE) (ISC).
