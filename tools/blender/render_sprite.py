"""Render a GLB as top-down sprite frames: the pre-rendered-CG route for Space Case flagships.

    /Applications/Blender.app/Contents/MacOS/Blender -b -P tools/blender/render_sprite.py -- \
        <model.glb> <out-dir> [--frames 12] [--size 512] [--box WxH] [--side] [--states idle,hurt,rage,death]

Writes <out-dir>/<n>.png for the idle loop and <out-dir>/<state>/<n>.png for the others, square frames
with transparency. The camera looks straight down (-Z), the model's nose faces -Y (down on screen), and
the model is scaled to fit the box aspect inside the frame so the game's drawFitted gets a full frame.

Motion is procedural, no rig needed: a slow sway and breathe on the whole body, an emissive pulse on
every material that emits, growing redder and faster through hurt and rage; death is a collapse: the
body tips, sinks and darkens over the frames while the emissives flare then die.
"""
import math, os, sys
import bpy
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
if len(argv) < 2:
    print(__doc__); sys.exit(2)
model, out = argv[0], argv[1]
opt = {"frames": 12, "size": 512, "box": None, "side": False, "states": "idle"}
i = 2
while i < len(argv):
    k = argv[i].lstrip("-")
    if k == "side": opt["side"] = True; i += 1
    else: opt[k] = argv[i + 1]; i += 2
FRAMES, SIZE = int(opt["frames"]), int(opt["size"])
STATES = opt["states"].split(",")
box_w, box_h = (map(float, opt["box"].lower().split("x")) if opt["box"] else (1.0, 1.0))

# ---- scene -----------------------------------------------------------------------------------
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.engine = "BLENDER_EEVEE_NEXT" if hasattr(bpy.types, "SceneEEVEE") and "BLENDER_EEVEE_NEXT" in [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items] else "BLENDER_EEVEE"
scene.render.resolution_x = scene.render.resolution_y = SIZE
scene.render.film_transparent = True
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
transforms = [v.identifier for v in bpy.types.ColorManagedViewSettings.bl_rna.properties["view_transform"].enum_items]
scene.view_settings.view_transform = "AgX" if "AgX" in transforms else "Filmic" if "Filmic" in transforms else "Standard"
scene.view_settings.exposure = -0.5

bpy.ops.import_scene.gltf(filepath=model)
meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
if not meshes:
    print("no meshes in", model); sys.exit(1)
root = bpy.data.objects.new("root", None); scene.collection.objects.link(root)
for o in meshes:
    if o.parent is None: o.parent = root

# Fit: centre the body, then scale so its XY footprint matches the box aspect inside 90% of the frame.
def bounds():
    pts = [o.matrix_world @ Vector(c) for o in meshes for c in o.bound_box]
    lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
    hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
    return lo, hi
bpy.context.view_layer.update()
lo, hi = bounds(); centre = (lo + hi) / 2
for o in meshes:
    if o.parent is root: o.location -= centre
# Sprixen GLBs face +X after import; the game wants the nose at -Y (down the screen). --side turns it a further quarter.
root.rotation_euler.z = math.radians(-90 + (90 if opt["side"] else 0))
HEADING = root.rotation_euler.z
bpy.context.view_layer.update()
lo, hi = bounds(); ext = hi - lo
target_w, target_h = (0.9, 0.9 * box_h / box_w) if box_w >= box_h else (0.9 * box_w / box_h, 0.9)
s = min(target_w / max(ext.x, 1e-6), target_h / max(ext.y, 1e-6))
root.scale = (s, s, s)
bpy.context.view_layer.update()
lo, hi = bounds()

# Camera straight down, orthographic so the frame edges are the box edges.
cam_data = bpy.data.cameras.new("cam"); cam_data.type = "ORTHO"; cam_data.ortho_scale = 1.0
cam = bpy.data.objects.new("cam", cam_data); scene.collection.objects.link(cam); scene.camera = cam
cam.location = (0, 0, hi.z + 5); cam.rotation_euler = (0, 0, 0)

# Lights: cold key from above-left, warm underglow from the nose, a dim fill. Matches the style bible.
def light(name, kind, loc, colour, energy, size=2.0):
    d = bpy.data.lights.new(name, kind); d.color = colour; d.energy = energy
    if kind == "AREA": d.size = size
    o = bpy.data.objects.new(name, d); scene.collection.objects.link(o); o.location = loc
    o.rotation_euler = (Vector((0, 0, 0)) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    return o
light("key", "AREA", (-1.2, 1.0, 3.0), (0.39, 0.70, 1.0), 260, 2.5)
light("warm", "AREA", (0.3, -1.6, 1.2), (1.0, 0.55, 0.23), 90, 2.0)
light("fill", "AREA", (1.5, 0.2, 2.5), (0.6, 0.6, 0.7), 60, 3.0)

# Emissive materials pulse; collect them with their base strength.
emissive = []
for m in bpy.data.materials:
    if not m.use_nodes: continue
    for n in m.node_tree.nodes:
        if n.type == "BSDF_PRINCIPLED" and "Emission Strength" in n.inputs:
            col = n.inputs["Emission Color"].default_value[:3]
            if max(col) > 0.05 and n.inputs["Emission Strength"].default_value > 0:
                emissive.append((n, n.inputs["Emission Strength"].default_value, tuple(col)))
        if n.type == "EMISSION":
            emissive.append((n, n.inputs["Strength"].default_value, tuple(n.inputs["Color"].default_value[:3])))
warm_light = bpy.data.objects["warm"].data
WARM_BASE = warm_light.energy

def set_emission(mult, redden):
    # No emissive material (a single baked texture, the usual TRELLIS output): pulse the underglow instead.
    warm_light.energy = WARM_BASE * (0.6 + 0.8 * mult) * (1 + redden)
    warm_light.color = (1.0, 0.55 * (1 - 0.5 * redden), 0.23 * (1 - 0.8 * redden))
    for n, base, col in emissive:
        strength = n.inputs["Emission Strength"] if n.type == "BSDF_PRINCIPLED" else n.inputs["Strength"]
        strength.default_value = base * mult
        c = (min(1, col[0] + redden), col[1] * (1 - 0.6 * redden), col[2] * (1 - 0.8 * redden), 1.0)
        (n.inputs["Emission Color"] if n.type == "BSDF_PRINCIPLED" else n.inputs["Color"]).default_value = c

def pose(t, state):
    """t in [0,1) around the loop (or along the death)."""
    a = 2 * math.pi * t
    if state == "death":
        k = t  # 0..1
        root.rotation_euler = (math.radians(18 * k), math.radians(-12 * k), HEADING + math.radians(6 * k))
        root.scale = (s * (1 - 0.15 * k), s * (1 - 0.15 * k), s * (1 - 0.3 * k))
        root.location = (0.04 * k, -0.05 * k, -0.5 * k)
        flare = 1 + 3.5 * math.sin(math.pi * min(1, k * 2)) if k < 0.5 else max(0.0, 1 - (k - 0.5) * 2)
        set_emission(flare, 0.8)
        return
    speed = {"idle": 1, "hurt": 2, "rage": 3}[state]
    sway = {"idle": 1.0, "hurt": 1.6, "rage": 2.4}[state]
    root.rotation_euler = (math.radians(1.5 * sway * math.sin(a * speed)), math.radians(2.0 * sway * math.sin(a * speed + 1.3)), HEADING + math.radians(0.8 * sway * math.sin(a * 2 * speed)))
    breathe = 1 + 0.02 * sway * math.sin(a * speed)
    root.scale = (s * breathe, s * breathe, s)
    root.location = (0, 0.01 * sway * math.sin(a * speed + 0.7), 0)
    pulse = 0.65 + 0.35 * math.sin(a * speed * 2)
    set_emission((1 + 0.4 * (speed - 1)) * (0.5 + pulse), {"idle": 0.0, "hurt": 0.35, "rage": 0.7}[state])

for state in STATES:
    d = out if state == "idle" else os.path.join(out, state)
    os.makedirs(d, exist_ok=True)
    for f in range(FRAMES):
        pose(f / FRAMES if state != "death" else f / max(1, FRAMES - 1), state)
        bpy.context.view_layer.update()
        scene.render.filepath = os.path.join(d, f"{f + 1}.png")
        bpy.ops.render.render(write_still=True)
    print("wrote", FRAMES, state, "frames to", d)
