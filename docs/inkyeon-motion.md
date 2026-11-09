# Inkyeon motion integration

The approved v14 geometry, paired normal/shiny UV textures, shutter blink, playful idle, brisk battle idle, native cry and pose are included. The battle idle lasts 2.666666667 seconds versus the 8-second playful idle.

Walking and swimming add `walk_alignment` between the ground stance and the gait. The original stance splays both rear hips by 2.5 degrees in yaw and roll, and rolls the rear feet by 3 degrees. Combining that splay with the gait's pitch rotation sends the feet through sideways arcs. The alignment cancels only those lateral angles while moving, so each rear leg swings in a forward/back plane.

The actual Cobblemon 1.8.4 side-view renders also showed the inherited Sylveon gait flinging the rear legs far forward and backward at stride extremes. Two native `q.biped_walk` helpers now retain the front stride at amplitude 1.4 and shorten only the rear stride to 0.7. Both retain frequency 0.66; the rear helper deliberately takes `back_leg_right` before `back_leg_left` to preserve the original diagonal stepping phases. At limb-swing amount 0.8, rear hip motion is reduced from about +/-64.17 degrees to +/-32.09 degrees around its 5-degree stance. Knee and ankle bends, smooth pose transitions, idle and battle animations, model geometry and both textures are unchanged.

The review viewer also has browser JavaScript collision physics and falling ink/puddle effects. Those browser-only effects are not supported by this resource-pack export and are not claimed to run in Minecraft. They remain preserved in the local review checkpoint.
