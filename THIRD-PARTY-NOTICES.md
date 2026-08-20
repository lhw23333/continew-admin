# Third-Party Notices

This project distributes and uses third-party software. This notice is a release aid, not a substitute for the complete dependency SBOM or legal advice.

## Flowable

- Component: Flowable Process Engine and the transitive modules selected by `flowable-spring-boot-starter-process`
- Selected artifact version: `7.1.0` (engine version constant `7.1.0.2`)
- Project: <https://github.com/flowable/flowable-engine>
- License declared by the upstream POM: Apache License, Version 2.0
- License text: the repository root [LICENSE](LICENSE) contains the Apache License, Version 2.0

When Flowable binaries or modified sources are redistributed, retain upstream copyright, patent, attribution, and license notices. Mark material source modifications and preserve any upstream `NOTICE` content included by the exact resolved artifacts. Flowable trademarks are not granted by the Apache license.

## ContiNew Starter

- Component family: `top.continew.starter:*`
- Selected parent/BOM version: `2.14.0`
- Project: <https://github.com/continew-org/continew-starter>
- License declared by the `2.14.0` upstream POM: GNU Lesser General Public License (the POM does not state an SPDX version)

Before external distribution, release engineering must retrieve and retain the exact license and notice files corresponding to the resolved `2.14.0` source release. The distribution must:

1. Preserve ContiNew Starter copyright and license notices and provide the applicable LGPL text.
2. Keep a written record of the exact Starter artifacts and source revision used.
3. Provide the corresponding source, or the applicable durable source offer, for any distributed LGPL-covered Starter code and modifications as required by the applicable license version.
4. License modifications to LGPL-covered Starter code under the applicable LGPL terms and identify those modifications.
5. Keep Starter libraries replaceable/relinkable by recipients and do not impose terms or technical controls that prohibit reverse engineering for debugging such modifications where the LGPL requires it.

Application code that only uses Starter through normal Java library linkage remains separately licensed, subject to final legal review of the exact upstream license text and distribution model. Copying Starter source into project-owned modules changes this assessment and requires a new review.

## Release Evidence

The generated CycloneDX JSON and XML files are the authoritative inventory of resolved release dependencies. Generation, validation, storage, and review requirements are defined in [docs/release/open-source-compliance.md](docs/release/open-source-compliance.md).
