# Native3DGS Web SDK

Pinned package: `@native3dgs/web@0.2.2-preview.4`. Requires Node.js 24.14+.

Official release: https://github.com/XJI1234/Native3DGSViewer/releases/tag/v0.2.2

| Artifact | SHA256 |
| --- | --- |
| Native3DGS-SDK-0.2.2-Web-WebGPU-preview.4.zip | 7155065593376060f781851ab0621f8e77eee85645072f406c241528b3b871af |
| Native3DGS-Template-0.2.2-Web-Vue-preview.4.zip | 52a9dda663b30ece38c4b6bc168f0c415f7a4756d90c6f0273c4b675d24b7a4e |
| native3dgs-web-0.2.2-preview.4.tgz | 4bd93d8d91457fea43dc9ff39258e3c34f084b495ec9880c7112ab3cf4b46554 |

SDK source: `4fa4a30f50eebe3f3cfc22f54a3bc036c693ea6e`.
Vue template source: `38ff52ffc0daaf1c7908c0c519afbd57a0b1ba84`.
Both archives carry this exact tarball. The release tag itself is older than the Web SDK source.

The package contains SDK/Vue integration documentation. Preview.4 adds bounded SPZ v4 Zstd/OPFS decoding and optional `snapshot.decoder` allocation diagnostics. `scripts/copy-native3dgs-assets.mjs` validates its asset manifest and copies the complete installed assets, including threaded decoders and third-party licenses. Do not edit this archive, mix decoder versions, or commit generated public assets.

The SDK archive grants no additional license for repository-owned code; repository owner terms apply. Preserve its THIRD-PARTY-NOTICES and assets/licenses when deploying. This integration is requested by the repository owner's team; no MIT or other license is inferred.
