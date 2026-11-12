"""Procedural 3D asteroids, rendered as seamless tumble loops for the game's asteroid sprites.

    /Applications/Blender.app/Contents/MacOS/Blender -b -P tools/blender/asteroids.py -- \
        [src/main/resources/sprites] [--frames 32] [--size 256]

Writes <out>/asteroid-{small,big,huge}/<n>.png. Each rock is an icosphere pushed into a lumpy potato by
fBm noise, then cratered (bowl plus raised rim) at seeded points; Cycles renders it straight down,
orthographic, lit from the upper left like the rest of the arena. Frame n is a 360/N turn about a tilted
axis, so the loop is seamless and the rock shows new faces as it goes rather than spinning flat.
Seeded, so a rerun rebuilds the same rocks.
"""
import math, os, random, sys
import bpy, bmesh
from mathutils import Vector, Quaternion, noise

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
out = argv[0] if argv and not argv[0].startswith("--") else "src/main/resources/sprites"
opt = {"frames": 32, "size": 256}
for k, v in zip(argv[::1], argv[1::1]):
    if k.lstrip("-") in opt: opt[k.lstrip("-")] = int(v)
FRAMES, SIZE = opt["frames"], opt["size"]

# name, seed, stretch, crater count, tumble axis, base colour (linear)
ROCKS = [
    ("asteroid-small", 11, (1.0, 0.78, 0.7), 7, (0.4, 1.0, 0.3), (0.16, 0.14, 0.12)),
    ("asteroid-big", 23, (1.0, 0.85, 0.66), 12, (1.0, 0.35, 0.5), (0.13, 0.12, 0.115)),
    ("asteroid-huge", 37, (1.0, 0.9, 0.75), 20, (0.25, 0.6, 1.0), (0.15, 0.13, 0.11)),
]


def scene_setup():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    s = bpy.context.scene
    s.render.engine = "CYCLES"
    s.cycles.samples = 64
    s.cycles.use_denoising = True
    s.render.resolution_x = s.render.resolution_y = SIZE
    s.render.film_transparent = True
    s.render.image_settings.file_format = "PNG"
    s.render.image_settings.color_mode = "RGBA"
    s.view_settings.view_transform = "AgX"; s.view_settings.look = "AgX - Punchy"
    cam_data = bpy.data.cameras.new("cam"); cam_data.type = "ORTHO"; cam_data.ortho_scale = 2.3
    cam = bpy.data.objects.new("cam", cam_data); s.collection.objects.link(cam); s.camera = cam
    cam.location = (0, 0, 10)
    # Key from the upper left of the screen (screen up is +Y), a cold rim from lower right, dim fill.
    for name, rot, colour, energy in [("key", (math.radians(-40), math.radians(-35), 0), (1.0, 0.95, 0.88), 3.2),
                                      ("rim", (math.radians(70), math.radians(60), 0), (0.55, 0.7, 1.0), 0.9)]:
        d = bpy.data.lights.new(name, "SUN"); d.color = colour; d.energy = energy; d.angle = math.radians(3)
        o = bpy.data.objects.new(name, d); o.rotation_euler = rot; s.collection.objects.link(o)
    world = bpy.data.worlds.new("w"); s.world = world; world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.08
    return s


def rock_mesh(seed, stretch, craters):
    rnd = random.Random(seed)
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=6, radius=1.0)
    offset = Vector((rnd.uniform(-50, 50), rnd.uniform(-50, 50), rnd.uniform(-50, 50)))
    pits = [(Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1))).normalized(),
             rnd.uniform(0.12, 0.42) * (0.6 if i > craters // 2 else 1.0), rnd.uniform(0.6, 1.0))
            for i in range(craters)]
    for v in bm.verts:
        n = v.co.normalized()
        r = 1.0 + 0.22 * noise.fractal(n * 0.9 + offset, 0.55, 2.0, 3)   # big lumps
        r += 0.025 * noise.fractal(n * 4.0 + offset, 0.5, 2.1, 4)         # gravel
        for centre, size, depth in pits:
            d = (n - centre).length / size
            if d < 1.0:
                r -= 0.5 * size * depth * (1 - d * d) ** 1.5      # bowl
            elif d < 1.45:
                r += 0.12 * size * depth * math.sin((d - 1.0) / 0.45 * math.pi)  # rim
        v.co = Vector((n.x * stretch[0], n.y * stretch[1], n.z * stretch[2])) * r
    me = bpy.data.meshes.new("rock"); bm.to_mesh(me); bm.free()
    for p in me.polygons: p.use_smooth = True
    return me


def rock_material(seed, base):
    m = bpy.data.materials.new(f"rock{seed}"); m.use_nodes = True
    nt = m.node_tree; N = nt.nodes; L = nt.links
    bsdf = N["Principled BSDF"]
    bsdf.inputs["Roughness"].default_value = 0.92
    coord = N.new("ShaderNodeTexCoord")
    mottle = N.new("ShaderNodeTexNoise"); mottle.inputs["Scale"].default_value = 2.6 + seed % 7 * 0.15
    mottle.inputs["Detail"].default_value = 8
    ramp = N.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].color = (*[c * 0.55 for c in base], 1)
    ramp.color_ramp.elements[1].color = (*[min(1, c * 2.4) for c in base], 1)
    L.new(coord.outputs["Object"], mottle.inputs["Vector"])
    L.new(mottle.outputs["Fac"], ramp.inputs["Fac"])
    L.new(ramp.outputs["Color"], bsdf.inputs["Base Color"])
    grit = N.new("ShaderNodeTexNoise"); grit.inputs["Scale"].default_value = 40.0; grit.inputs["Detail"].default_value = 6
    bump = N.new("ShaderNodeBump"); bump.inputs["Strength"].default_value = 0.6
    L.new(coord.outputs["Object"], grit.inputs["Vector"])
    L.new(grit.outputs["Fac"], bump.inputs["Height"])
    L.new(bump.outputs["Normal"], bsdf.inputs["Normal"])
    return m


s = scene_setup()
for name, seed, stretch, craters, axis, base in ROCKS:
    obj = bpy.data.objects.new(name, rock_mesh(seed, stretch, craters))
    obj.data.materials.append(rock_material(seed, base))
    s.collection.objects.link(obj)
    # Fit the widest extent over any turn into the frame: the radius of the furthest vertex.
    reach = max(v.co.length for v in obj.data.vertices)
    obj.scale = (0.95 * 1.15 / reach,) * 3
    obj.rotation_mode = "QUATERNION"
    start = Quaternion(Vector((1, 0.3, 0.2)).normalized(), seed)
    os.makedirs(os.path.join(out, name), exist_ok=True)
    for f in range(FRAMES):
        obj.rotation_quaternion = Quaternion(Vector(axis).normalized(), 2 * math.pi * f / FRAMES) @ start
        s.render.filepath = os.path.abspath(os.path.join(out, name, f"{f + 1}.png"))
        bpy.ops.render.render(write_still=True)
    obj.hide_render = True
