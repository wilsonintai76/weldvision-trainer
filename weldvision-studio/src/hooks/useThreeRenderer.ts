/**
 * useThreeRenderer — manages the Three.js weld visualization canvas.
 *
 * Consumes StudentTelemetryFrame[] history and renders it as a 3D ribbon
 * with torch indicator. Handles playback (play/pause/speed/reset).
 */

import { useRef, useEffect, useCallback, useState } from "react";
import * as THREE from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import type { StudentTelemetryFrame } from "../lib/types";

interface UseThreeRendererReturn {
  containerRef: React.RefObject<HTMLDivElement | null>;
  isPlaying: boolean;
  currentFrame: number;
  totalFrames: number;
  play: () => void;
  pause: () => void;
  reset: () => void;
  setSpeed: (speed: number) => void;
  loadHistory: (frames: StudentTelemetryFrame[]) => void;
}

const MAX_TIP_RADIUS_MM = 500;

export function useThreeRenderer(): UseThreeRendererReturn {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const sceneRef = useRef<{
    scene: THREE.Scene;
    camera: THREE.PerspectiveCamera;
    renderer: THREE.WebGLRenderer;
    controls: OrbitControls;
    ribbonGeo: THREE.BufferGeometry;
    ribbonLine: THREE.Line;
    torchSphere: THREE.Mesh;
  } | null>(null);

  const framesRef = useRef<StudentTelemetryFrame[]>([]);
  const frameIndexRef = useRef(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentFrame, setCurrentFrame] = useState(0);
  const [totalFrames, setTotalFrames] = useState(0);
  const speedRef = useRef(1);
  const lastFrameTimeRef = useRef(0);
  const FRAME_INTERVAL_MS = 1000 / 60;

  // ── Initialize Three.js ────────────────────────────────────────────────

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    // Scene
    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0d1117);
    scene.fog = new THREE.Fog(0x0d1117, 800, 2000);

    // Camera
    const camera = new THREE.PerspectiveCamera(50, 2, 10, 3000);
    camera.position.set(300, 200, 400);
    camera.lookAt(0, 0, 100);

    // Renderer
    const renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.shadowMap.enabled = true;
    container.appendChild(renderer.domElement);

    // Controls
    const controls = new OrbitControls(camera, renderer.domElement);
    controls.target.set(0, 0, 100);
    controls.enableDamping = true;
    controls.update();

    // Lighting
    scene.add(new THREE.AmbientLight(0x404060, 1.5));
    const dirLight = new THREE.DirectionalLight(0xffffff, 2);
    dirLight.position.set(200, 400, 300);
    dirLight.castShadow = true;
    scene.add(dirLight);

    // T-Joint fixture
    const plateGeom = new THREE.BoxGeometry(300, 4, 200);
    const plateMat = new THREE.MeshStandardMaterial({
      color: 0x4a5568,
      roughness: 0.6,
      metalness: 0.4,
    });
    const basePlate = new THREE.Mesh(plateGeom, plateMat);
    basePlate.position.set(0, -2, 100);
    basePlate.receiveShadow = true;
    scene.add(basePlate);

    const vertPlate = new THREE.Mesh(
      new THREE.BoxGeometry(4, 100, 200),
      new THREE.MeshStandardMaterial({ color: 0x5a6578, roughness: 0.6, metalness: 0.4 })
    );
    vertPlate.position.set(0, 48, 100);
    vertPlate.receiveShadow = true;
    scene.add(vertPlate);

    const grid = new THREE.GridHelper(400, 20, 0x30363d, 0x21262d);
    grid.position.set(0, -4, 100);
    scene.add(grid);

    // Axes
    const makeAxis = (x: number, y: number, z: number, color: number) => {
      const geo = new THREE.BufferGeometry().setFromPoints([
        new THREE.Vector3(0, 0, 0),
        new THREE.Vector3(x, y, z),
      ]);
      scene.add(new THREE.Line(geo, new THREE.LineBasicMaterial({ color })));
    };
    makeAxis(150, 0, 0, 0xff4444);
    makeAxis(0, 150, 0, 0x44ff44);
    makeAxis(0, 0, 150, 0x4488ff);

    // Ribbon
    const ribbonGeo = new THREE.BufferGeometry();
    const ribbonLine = new THREE.Line(
      ribbonGeo,
      new THREE.LineBasicMaterial({ color: 0x58a6ff })
    );
    scene.add(ribbonLine);

    // Torch sphere
    const torchSphere = new THREE.Mesh(
      new THREE.SphereGeometry(4, 16, 16),
      new THREE.MeshStandardMaterial({
        color: 0xff7b00,
        emissive: 0xff4400,
        emissiveIntensity: 0.6,
      })
    );
    torchSphere.visible = false;
    scene.add(torchSphere);

    sceneRef.current = {
      scene,
      camera,
      renderer,
      controls,
      ribbonGeo,
      ribbonLine,
      torchSphere,
    };

    // Resize
    const resize = () => {
      const rect = container.getBoundingClientRect();
      camera.aspect = rect.width / Math.max(rect.height, 1);
      camera.updateProjectionMatrix();
      renderer.setSize(rect.width, rect.height);
    };
    resize();
    window.addEventListener("resize", resize);

    // Animation loop
    let animId: number;
    function animate(timestamp: number) {
      animId = requestAnimationFrame(animate);

      if (isPlaying && framesRef.current.length > 0) {
        const elapsed = timestamp - lastFrameTimeRef.current;
        const interval = FRAME_INTERVAL_MS / speedRef.current;

        if (elapsed >= interval) {
          lastFrameTimeRef.current = timestamp - (elapsed % interval);
          const next = Math.min(frameIndexRef.current + 1, framesRef.current.length - 1);
          frameIndexRef.current = next;
          setCurrentFrame(next);
          updateRibbonTo(next);
        }
      }

      controls.update();
      renderer.render(scene, camera);
    }
    animId = requestAnimationFrame(animate);

    return () => {
      window.removeEventListener("resize", resize);
      cancelAnimationFrame(animId);
      renderer.dispose();
      controls.dispose();
      container.removeChild(renderer.domElement);
    };
  }, []);

  // ── Ribbon update ──────────────────────────────────────────────────────

  const updateRibbonTo = useCallback((idx: number) => {
    const s = sceneRef.current;
    if (!s) return;

    const frames = framesRef.current.slice(0, idx + 1);
    const pts = frames.map((f) => {
      const x = f.spatial.x;
      const y = f.spatial.y;
      const z = -f.spatial.z; // Flip Z for Three.js
      return new THREE.Vector3(x, y, z);
    });

    s.ribbonGeo.setFromPoints(pts);
    if (pts.length > 0) {
      s.torchSphere.position.copy(pts[pts.length - 1]);
      s.torchSphere.visible = true;
    }
  }, []);

  // ── Public API ─────────────────────────────────────────────────────────

  const loadHistory = useCallback(
    (frames: StudentTelemetryFrame[]) => {
      framesRef.current = frames;
      frameIndexRef.current = 0;
      setTotalFrames(frames.length);
      setCurrentFrame(0);
      sceneRef.current?.ribbonGeo.setFromPoints([]);
      if (sceneRef.current) sceneRef.current.torchSphere.visible = false;
    },
    []
  );

  const play = useCallback(() => {
    setIsPlaying(true);
    lastFrameTimeRef.current = performance.now();
  }, []);

  const pause = useCallback(() => {
    setIsPlaying(false);
  }, []);

  const reset = useCallback(() => {
    setIsPlaying(false);
    frameIndexRef.current = 0;
    setCurrentFrame(0);
    sceneRef.current?.ribbonGeo.setFromPoints([]);
    if (sceneRef.current) sceneRef.current.torchSphere.visible = false;
  }, []);

  const setSpeed = useCallback((s: number) => {
    speedRef.current = s;
  }, []);

  return {
    containerRef,
    isPlaying,
    currentFrame,
    totalFrames,
    play,
    pause,
    reset,
    setSpeed,
    loadHistory,
  };
}
