"""Reproducible local mace source. Run with Blender --background --python this_file."""
import bpy, math, random, json
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/3D/weapons/mace'
SOURCE = ROOT / 'asset-source/weapons/mace'
OUT.mkdir(parents=True, exist_ok=True)
SOURCE.mkdir(parents=True, exist_ok=True)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
random.seed(782)

def material(name, color, metallic, noise):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bs = m.node_tree.nodes.get('Principled BSDF')
    bs.inputs['Metallic'].default_value = metallic
    bs.inputs['Roughness'].default_value = .76
    img = bpy.data.images.new(name + '_wear', width=256, height=256)
    pixels = []
    for y in range(256):
        for x in range(256):
            n = random.uniform(-noise, noise)
            scratch = -.14 if (x * 17 + y * 3) % 193 < 2 else 0
            grain = .025 * math.sin(x * .7 + math.sin(y * .02) * 3)
            pixels.extend([max(.015, min(.95, c + n + scratch + grain)) for c in color] + [1])
    img.pixels[:] = pixels
    img.pack()
    tex = m.node_tree.nodes.new('ShaderNodeTexImage')
    tex.image = img
    m.node_tree.links.new(tex.outputs['Color'], bs.inputs['Base Color'])
    return m

metal = material('tier_metal', (.48, .48, .48), .8, .09)
wood = material('dark_wood', (.105, .056, .026), 0, .025)
leather = material('worn_leather', (.14, .075, .040), 0, .04)
trim = material('blackened_fixed_iron', (.085, .079, .07), .65, .025)

def finish(obj, name, mat, bevel=0):
    obj.name = name
    obj.data.materials.append(mat)
    if bevel:
        mod = obj.modifiers.new('Worn edge bevel', 'BEVEL')
        mod.width = bevel
        mod.segments = 1
        bpy.context.view_layer.objects.active = obj
        bpy.ops.object.modifier_apply(modifier=mod.name)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.smart_project(island_margin=.03)
    bpy.ops.object.mode_set(mode='OBJECT')
    return obj

def cyl(name, r, depth, z, mat, vertices=10, bevel=.002):
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=r, depth=depth, location=(0,0,z))
    return finish(bpy.context.object, name, mat, bevel)

cyl('Wooden_shaft', .023, .53, .285, wood)
cyl('Leather_grip', .031, .26, .17, leather, 10)
cyl('Grip_lower_band', .034, .018, .043, trim)
cyl('Grip_upper_band', .033, .02, .303, trim)
cyl('Pommel', .043, .032, .018, trim, 8, .005)
# Narrow raised leather winding, still faceted rather than smooth/high-density.
for i in range(11):
    z = .055 + i * .023
    cyl('Leather_wrap_%02d' % i, .0325, .006, z, leather, 10, .001)
cyl('Head_socket', .039, .042, .49, metal, 8)
cyl('Head_core', .048, .18, .585, metal, 8, .003)
cyl('Head_crown', .040, .023, .683, metal, 8, .004)
# Six thick chamfered flanges with clipped shoulders and blunt striking edges.
outline = [(.032,.503),(.07,.517),(.109,.55),(.117,.607),(.089,.653),(.041,.679)]
for n in range(6):
    a = n * math.tau / 6
    verts = []
    for t in [-.009,.009]:
        for r,z in outline:
            verts.append((r*math.cos(a)-t*math.sin(a),r*math.sin(a)+t*math.cos(a),z))
    faces = [tuple(reversed(range(6))),tuple(range(6,12))]
    faces += [(i,(i+1)%6,(i+1)%6+6,i+6) for i in range(6)]
    mesh = bpy.data.meshes.new('Flange')
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new('Head_flange_%d' % n, mesh)
    bpy.context.collection.objects.link(obj)
    finish(obj, obj.name, metal, .003)

grip = bpy.data.objects.new('FP_GRIP_PRIMARY', None)
bpy.context.collection.objects.link(grip)
grip.location = (0,0,.17)
grip.empty_display_type = 'PLAIN_AXES'
grip.empty_display_size = .05
asset_objects = list(bpy.context.scene.objects)
bpy.ops.object.select_all(action='DESELECT')
for obj in asset_objects: obj.select_set(True)
bpy.ops.export_scene.gltf(filepath=str(OUT/'gritty_mace.glb'), export_format='GLB',
    use_selection=True, export_yup=True, export_apply=True)
triangles = sum(len(p.vertices)-2 for o in asset_objects if o.type=='MESH' for p in o.data.polygons)
(SOURCE/'asset-info.json').write_text(json.dumps({'generator':'Blender local procedural',
    'triangles':triangles,'heightMeters':.6945,'tierMaterial':'tier_metal',
    'gripNode':'FP_GRIP_PRIMARY','model':'assets/3D/weapons/mace/gritty_mace.glb'},indent=2))

# Studio render of the delivered geometry; camera/lights are not exported into GLB.
scene = bpy.context.scene
scene.render.engine = 'CYCLES'
scene.cycles.samples = 32
scene.render.resolution_x = 700
scene.render.resolution_y = 900
scene.render.resolution_percentage = 100
scene.world.color = (.09,.09,.09)
bpy.ops.object.camera_add(location=(1.05,-1.6,.94))
cam = bpy.context.object
cam.rotation_euler = (Vector((0,0,.35))-cam.location).to_track_quat('-Z','Y').to_euler()
cam.data.type = 'ORTHO'
cam.data.ortho_scale = .90
scene.camera = cam
for location, energy, size in [((1,-2,2),160,2),((-1,-.5,1),100,1.5),((0,1,1.3),210,1)]:
    bpy.ops.object.light_add(type='AREA',location=location)
    light = bpy.context.object
    light.data.energy = energy
    light.data.shape = 'DISK'
    light.data.size = size
    light.rotation_euler = (Vector((0,0,.4))-light.location).to_track_quat('-Z','Y').to_euler()
scene.render.film_transparent = True
scene.render.image_settings.file_format = 'PNG'
scene.render.filepath = str(SOURCE/'preview.png')
bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE/'gritty_mace.blend'))
bpy.ops.render.render(write_still=True)
# A bitmap fallback for the ground-item sprite; live inventory icons use the GLB.
scene.render.resolution_x = 192
scene.render.resolution_y = 192
cam.data.ortho_scale = .82
scene.render.filepath = str(OUT/'mace_icon.png')
bpy.ops.render.render(write_still=True)
# Sidecar mask keeps the ground sprite's grip unchanged when the item tier changes.
for mat in [metal, wood, leather, trim]:
    mat.node_tree.nodes.clear()
    out = mat.node_tree.nodes.new('ShaderNodeOutputMaterial')
    emission = mat.node_tree.nodes.new('ShaderNodeEmission')
    emission.inputs['Color'].default_value = (1,1,1,1) if mat == metal else (0,0,0,1)
    mat.node_tree.links.new(emission.outputs[0], out.inputs['Surface'])
scene.view_settings.view_transform = 'Standard'
scene.render.filepath = str(OUT/'mace_icon.tint-mask.png')
bpy.ops.render.render(write_still=True)
print('MACE_RESULT ' + str(triangles) + ' triangles ' + str(OUT/'gritty_mace.glb'))
