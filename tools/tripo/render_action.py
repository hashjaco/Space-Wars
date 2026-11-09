"""Blender (headless) side of tools/tripo/onfoot.py: a rigged character GLB -> side-view PNG frames of one move.

    Blender -b --factory-startup -P render_action.py -- model.glb out_dir size_px frames yaw (action <name> | poses <json> | list)

The camera is orthographic, side-on, the character facing the RIGHT of the frame, and FIXED for a character:
it is sized from the rest pose's height alone, with the floor (z=0) at a fixed row near the bottom. So every
move of one character shares a scale and a floor line, and the importer can stack them without re-fitting.

  action <name>  plays a GLB animation (a Tripo preset retargeted onto the rig) across the frames
  poses <json>   keys bone rotations on the rest pose: {"keys": [[t, {"bone": [x, y, z], ...}], ...]} with t
                 in 0..1 and Euler degrees added to the rest rotation; frames sample t evenly, eased between keys
  list           prints the armature's bone names and the GLB's actions, renders nothing

yaw (degrees) turns the model about Z first, for a model that does not come in facing -Y.
"""
import json, math, sys
import bpy
from mathutils import Euler, Vector

argv = sys.argv[sys.argv.index("--") + 1:]
glb, out, size, frames, yaw, mode = argv[0], argv[1], int(argv[2]), int(argv[3]), float(argv[4]), argv[5]
arg = argv[6] if len(argv) > 6 else None

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=glb)
scene = bpy.context.scene
arm = next((o for o in scene.objects if o.type == "ARMATURE"), None)
# Only what the rig moves: Tripo's GLBs also carry a stray unit Icosphere that would set the framing.
for o in [o for o in scene.objects if o.type == "MESH" and o.parent is not arm]:
    bpy.data.objects.remove(o, do_unlink=True)
meshes = [o for o in scene.objects if o.type == "MESH"]

if mode == "list":
    print("BONES", json.dumps([b.name for b in arm.data.bones]) if arm else "[]")
    print("ACTIONS", json.dumps([a.name for a in bpy.data.actions]))
    sys.exit(0)

# Turn everything about the world origin, which is where Tripo stands the character.
root = bpy.data.objects.new("root", None); scene.collection.objects.link(root)
for o in scene.objects:
    if o.parent is None and o is not root:
        o.parent = root
root.rotation_euler = (0, 0, math.radians(yaw))

# Rest-pose height, for the fixed camera. Taken before any animation is applied.
if arm and arm.animation_data:
    arm.animation_data.action = None
for pb in (arm.pose.bones if arm else []):
    pb.rotation_quaternion = (1, 0, 0, 0); pb.rotation_euler = (0, 0, 0); pb.location = (0, 0, 0)
bpy.context.view_layer.update()
dg = bpy.context.evaluated_depsgraph_get()
zs = []
for o in meshes:
    ev = o.evaluated_get(dg)
    zs += [(ev.matrix_world @ v.co).z for v in ev.to_mesh().vertices]
    ev.to_mesh_clear()
height = max(zs) - min(0, min(zs))

scene.render.engine = "BLENDER_EEVEE"
scene.render.film_transparent = True
scene.render.resolution_x = scene.render.resolution_y = size
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
scene.view_settings.view_transform = "Standard"
scene.world = bpy.data.worlds.new("w"); scene.world.color = (0.05, 0.05, 0.06)

# Fixed framing: 2.2 heights across, so a kick, a lunge or a jump stays in frame; floor 12% up from the bottom.
span = height * 2.2
cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
cam.data.type = "ORTHO"; cam.data.ortho_scale = span; cam.data.clip_end = span * 20
scene.collection.objects.link(cam); scene.camera = cam
cam.location = (-span * 3, 0, span * 0.5 - span * 0.12)
cam.rotation_euler = Vector((1, 0, 0)).to_track_quat("-Z", "Y").to_euler()
print("FLOOR_ROW", round(size * (1 - 0.12)), "HEIGHT_PX", round(size * height / span))

def light(name, energy, direction, colour):
    l = bpy.data.objects.new(name, bpy.data.lights.new(name, "SUN"))
    l.data.energy = energy; l.data.color = colour
    l.rotation_euler = Vector(direction).to_track_quat("-Z", "Y").to_euler()
    scene.collection.objects.link(l)

# The on-foot stage's lighting: a cold blue key from the camera side and above, a warm furnace rim from behind.
# Strong enough to read on the dark stage, soft enough to keep Tripo's dark materials dark (9/4/3/8 washed them out).
light("key", 4.5, (1, 0.6, -0.7), (0.78, 0.88, 1.0))
light("top", 2.0, (0.2, 0, -1), (1.0, 1.0, 1.0))
light("fill", 1.5, (0.6, -0.4, 0.6), (0.9, 0.9, 1.0))
light("rim", 6.0, (-1, -0.3, -0.2), (1.0, 0.55, 0.2))

def shoot(i):
    scene.render.filepath = f"{out}/{i + 1:04d}.png"
    bpy.ops.render.render(write_still=True)

if mode == "action":
    name, _, span_arg = arg.partition("@")     # "fall@0,0.3": only that fraction of the clip
    action = next(a for a in bpy.data.actions if a.name == name or name in a.name)
    arm.animation_data_create(); arm.animation_data.action = action
    start, end = action.frame_range
    if span_arg:
        f0, f1 = (float(v) for v in span_arg.split(","))
        start, end = start + (end - start) * f0, start + (end - start) * f1
    arg = name
    for i in range(frames):
        # A loop (walk, idle) samples [start, end); a one-shot reaches its last frame.
        t = start + (end - start) * (i / frames if arg in ("idle", "walk", "run") else i / max(1, frames - 1))
        scene.frame_set(int(t), subframe=t - int(t))
        shoot(i)
else:  # poses
    keys = json.load(open(arg))["keys"]
    for pb in arm.pose.bones:
        pb.rotation_mode = "XYZ"   # the poses are written as Euler degrees; glTF bones arrive as quaternions
    ease = lambda u: u * u * (3 - 2 * u)
    for i in range(frames):
        t = i / max(1, frames - 1)
        k = max(j for j in range(len(keys)) if keys[j][0] <= t + 1e-9)
        a, b = keys[k], keys[min(k + 1, len(keys) - 1)]
        u = 0 if b[0] == a[0] else ease((t - a[0]) / (b[0] - a[0]))
        for pb in arm.pose.bones:
            ra = a[1].get(pb.name, [0, 0, 0]); rb = b[1].get(pb.name, [0, 0, 0])
            pb.rotation_euler = Euler([math.radians(ra[j] + (rb[j] - ra[j]) * u) for j in range(3)], "XYZ")
        bpy.context.view_layer.update()
        shoot(i)
