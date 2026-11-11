"""Blender (headless) side of tools/tripo: one GLB -> a seamless loop of PNG frames.

    Blender -b --factory-startup -P render_blender.py -- model.glb out_dir yaw_degrees size_px frames [view] [motion] [tilt] [zoom]

yaw turns the model nose-down as seen from above (the per-model number in the manifests). tilt,
"rx,ry,rz" in degrees, is applied to the model first, for a Tripo model that came out lying on
its side or standing on its tail. Then:

  view  top   orthographic camera straight down, nose toward the bottom of the frame (the default)
        side  orthographic camera on the beam, nose toward the RIGHT of the frame, up is up
        turn  perspective camera 25 degrees above, the model turning a full circle across the frames
  motion sway    a roll sway and a smaller pitch on a full sine period, no scale change (the default)
         twitch  rage: twice as fast, twice as wide, a jerk on top, red key light
         sink    death: a one-shot; tips, sinks and darkens to nothing over the frames
         thrust  bite: lunges toward the camera's down (or right, side-on) and back, on one sine
         none    held still (turn uses this; the turning is the motion)

zoom is the orthographic frame as a multiple of the model's span (1.08): wider leaves room for a
thrust, so a set of loops rendered at one zoom shares one scale.

Key light from the upper left (the side Renderer drops hull shadows away from).
"""
import math, sys
import bpy
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:]
glb, out, yaw, size, frames = argv[:5]
view = argv[5] if len(argv) > 5 else "top"
motion = argv[6] if len(argv) > 6 else ("none" if view == "turn" else "sway")
tilt = [math.radians(float(a)) for a in argv[7].split(",")] if len(argv) > 7 else [0, 0, 0]
zoom = float(argv[8]) if len(argv) > 8 else 1.08
yaw, size, frames = math.radians(float(yaw)), int(size), int(frames)

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=glb)
meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]

# Parent everything to one pivot at the model's centre so the motion turns about the middle.
pts = [o.matrix_world @ Vector(c) for o in meshes for c in o.bound_box]
lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
centre = (lo + hi) / 2
pivot = bpy.data.objects.new("pivot", None); bpy.context.scene.collection.objects.link(pivot)
tilter = bpy.data.objects.new("tilt", None); bpy.context.scene.collection.objects.link(tilter)
tilter.rotation_euler = tilt
tilter.parent = pivot
for o in bpy.context.scene.objects:
    if o.parent is None and o not in (pivot, tilter):
        o.parent = tilter
        o.location -= centre
pivot.rotation_mode = "ZYX"  # yaw first, then roll/pitch about screen axes

scene = bpy.context.scene
scene.render.engine = "BLENDER_EEVEE"
scene.render.film_transparent = True
scene.render.resolution_x = scene.render.resolution_y = size
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
scene.view_settings.view_transform = "Standard"  # keep Tripo's baked albedo; AgX washes it out
scene.world = bpy.data.worlds.new("w"); scene.world.color = (0.05, 0.05, 0.06)

span = max(hi.x - lo.x, hi.y - lo.y, hi.z - lo.z)
cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
cam.data.clip_end = span * 20
scene.collection.objects.link(cam); scene.camera = cam
# The model's nose points to -Y after yaw (screen-down from above).
if view == "top":
    cam.data.type = "ORTHO"; cam.data.ortho_scale = span * 1.08
    cam.location = (0, 0, span * 3)
    look = Vector((0, 0, -1))
elif view == "side":
    # From -X looking +X with Z up, screen-right is -Y: the nose.
    cam.data.type = "ORTHO"; cam.data.ortho_scale = span * zoom
    cam.location = (-span * 3, 0, 0)
    look = Vector((1, 0, 0))
else:  # turn
    cam.data.type = "PERSP"; cam.data.lens = 50
    elev = math.radians(25); dist = span * 2.6
    cam.location = (0, -dist * math.cos(elev), dist * math.sin(elev))
    look = -Vector(cam.location)
cam.rotation_euler = look.to_track_quat("-Z", "Y").to_euler()

def light(name, kind, energy, rot, colour=(1, 1, 1)):
    l = bpy.data.objects.new(name, bpy.data.lights.new(name, kind))
    l.data.energy = energy; l.data.color = colour; l.rotation_euler = [math.radians(a) for a in rot]
    scene.collection.objects.link(l)
    return l

key = light("key", "SUN", 6.0, (35, 0, -135), (1, 0.35, 0.3) if motion == "twitch" else (1, 1, 1))
fill = light("fill", "SUN", 2.2, (50, 0, 60))
rim = light("rim", "SUN", 2.5, (110, 0, 0))
def aim(l, direction):
    l.rotation_euler = Vector(direction).to_track_quat("-Z", "Y").to_euler()

if view == "side":  # key from the camera's side and above, fill from below, rim from behind
    aim(key, (1, 0.35, -0.8)); aim(fill, (0.6, -0.3, 0.7)); aim(rim, (-1, 0, -0.3))
if view == "turn":
    aim(key, (0.4, 0.8, -0.7)); aim(fill, (-0.6, 0.5, -0.3))

base = {l: l.data.energy for l in (key, fill, rim)}
for i in range(frames):
    t = 2 * math.pi * i / frames
    roll, pitch, scale = math.radians(3) * math.cos(t), math.radians(7) * math.sin(t), 1.0
    spin = yaw + (2 * math.pi * i / frames if view == "turn" else 0)
    shift = Vector((0, 0, 0))
    if motion == "none":
        roll = pitch = 0
    elif motion == "twitch":
        roll, pitch = math.radians(6) * math.cos(2 * t), math.radians(12) * math.sin(2 * t) + math.radians(3) * math.sin(7 * t)
    elif motion == "sink":
        p = i / max(1, frames - 1)
        roll, pitch, scale = math.radians(25) * p, math.radians(-30) * p, 1 - 0.35 * p
        for l, e in base.items():
            l.data.energy = e * (1 - 0.85 * p)
    elif motion == "thrust":
        lunge = math.sin(math.pi * i / frames)  # one push out and back across the loop
        roll, pitch, scale = 0, math.radians(-10) * lunge, 1 + 0.12 * lunge
        shift = Vector((0, -span * 0.08 * lunge, 0))  # toward the nose
    pivot.rotation_euler = (roll, pitch, spin)
    pivot.scale = (scale, scale, scale)
    pivot.location = shift
    scene.render.filepath = f"{out}/{i + 1:04d}.png"
    bpy.ops.render.render(write_still=True)
