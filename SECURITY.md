# Security policy

Otium keeps a personal reading library on the device. Reports about anything that could expose
that data, or the keys and accounts around it, are welcome.

## Supported versions

Otium has no stable release yet. Only the latest commit on `main` receives security fixes. Once
releases are published, this section will list the supported versions.

## Reporting a vulnerability

Please do not open a public issue, pull request or discussion for a vulnerability.

Report it privately with GitHub's private vulnerability reporting:

1. Open the [Security tab](https://github.com/eh0x/otium/security) of the repository.
2. Choose **Report a vulnerability**.
3. Describe the problem, the steps to reproduce it, and the version or commit you tested.

If that option is not available, open a public issue that asks for a private channel and contains
no details of the problem. A maintainer will reply with one.

## What to expect

- An acknowledgement within 7 days.
- An assessment of severity and impact, and a plan, within 14 days.
- A fix, or a clear reason why there is none, as fast as the severity allows. Critical problems
  come first.
- Credit in the release notes and the advisory, unless you would rather stay anonymous.

Otium is maintained by one person in spare time, so these are targets, not guarantees. If you
hear nothing within 7 days, please ask again in the same report.

## Coordinated disclosure

Please give the maintainer a reasonable time to ship a fix before you publish details. 90 days from
the first report is the usual limit, and it can be shorter when a fix is already out. Fixes for
confirmed problems are published as a GitHub security advisory, with a CVE where one applies.

## Scope

In scope:

- The Otium app and the code in this repository.
- Leaks of library data, notes or reading progress, and of anything stored on the device.
- Unsafe handling of API keys, tokens or credentials, including in the build and CI setup.
- Vulnerabilities in the app's dependencies that can be reached through Otium.

Out of scope:

- Problems that need a rooted or already compromised device.
- Problems in third-party services such as the Google Books API. Report those to the service.
- Reports from automated scanners with no demonstrated impact on Otium.

## Keys and secrets

API keys belong in `local.properties` or the environment and are never committed. If you find a
secret in the repository or its history, report it the same way, as a vulnerability.
