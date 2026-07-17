# WeldVision Core Assets

Single source of truth for all 3D models, print materials, and visual
assets shared between the Android student app and the WebVision Studio
instructor dashboard.

## Directory

```
core-assets/
├── models/           # glTF/GLB 3D models
│   ├── t_joint_coupon.glb    # T-joint weld coupon (dimensions from AWS spec)
│   └── mig_nozzle.glb        # MIG welding torch nozzle
├── prints/           # Printable calibration targets
│   └── apriltag_6x4_target.svg  # 6×4 tag grid for fixture alignment
└── README.md
```

## Model Specifications

### t_joint_coupon.glb
- Format: glTF 2.0 Binary (.glb)
- Geometry: Base plate (300×200×4 mm) + vertical stem (100×200×4 mm)
- Origin: Center of weld seam start point
- Axes: Y = along seam (travel), Z = up (CTWD), X = across seam

### mig_nozzle.glb
- Format: glTF 2.0 Binary (.glb)
- Reference point: Nozzle tip center
- Scale: 1 unit = 1 mm

## Usage

### Android (3D Preview)
Copy to `android-app/app/src/main/assets/models/`

### WebVision Studio (Three.js)
Import directly from `../core-assets/models/` via Vite's static asset handling.

### AprilTag Target
Print `prints/apriltag_6x4_target.svg` at 100% scale on A4 paper.
Affix to CNC-machined or 3D-printed calibration fixture.
