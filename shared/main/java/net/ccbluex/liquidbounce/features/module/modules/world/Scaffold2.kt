package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.api.minecraft.client.block.IBlock
import net.ccbluex.liquidbounce.api.minecraft.item.IItemStack
import net.ccbluex.liquidbounce.api.minecraft.util.WBlockPos
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleInfo
import net.ccbluex.liquidbounce.features.module.modules.render.BlockOverlay
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.InventoryUtils
import net.ccbluex.liquidbounce.utils.MovementUtils
import net.ccbluex.liquidbounce.utils.Rotation
import net.ccbluex.liquidbounce.utils.RotationUtils
import net.ccbluex.liquidbounce.utils.block.BlockUtils
import net.ccbluex.liquidbounce.utils.block.PlaceInfo
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.timer.MSTimer
import net.ccbluex.liquidbounce.value.BoolValue
import net.ccbluex.liquidbounce.value.FloatValue
import net.ccbluex.liquidbounce.value.IntegerValue
import net.ccbluex.liquidbounce.value.ListValue
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

@ModuleInfo(
    name = "Scaffold2",
    description = "Automatically places blocks beneath your feet.",
    category = ModuleCategory.WORLD,
    keyBind = Keyboard.KEY_C
)

class Scaffold2 : Module() {

    private val modeValue = ListValue("Mode", arrayOf("Normal", "Rewinside", "Expand"), "Normal")

    // Delay
    private val maxDelayValue: IntegerValue = object : IntegerValue("MaxDelay", 0, 0, 1000) {
        override fun onChanged(oldValue: Int, newValue: Int) {
            val minDelay = minDelayValue.get()
            if (minDelay > newValue) set(minDelay)
        }
    }

    private val minDelayValue: IntegerValue = object : IntegerValue("MinDelay", 0, 0, 1000) {
        override fun onChanged(oldValue: Int, newValue: Int) {
            val maxDelay = maxDelayValue.get()
            if (maxDelay < newValue) set(maxDelay)
        }
    }

    // Placeable delay
    private val placeableDelay = BoolValue("PlaceableDelay", true)

    // Autoblock
    private val autoBlockValue = ListValue("AutoBlock", arrayOf("Off", "Spoof", "Switch"), "Spoof")

    // Basic stuff
    @JvmField
    val sprintValue = BoolValue("Sprint", false)
    private val swingValue = BoolValue("Swing", true)
    private val searchValue = BoolValue("Search", true)
    private val downValue = BoolValue("Down", true)
    private val placeModeValue = ListValue("PlaceTiming", arrayOf("Pre", "Post"), "Post")

    // Eagle
    private val eagleValue = ListValue("Eagle", arrayOf("Normal", "EdgeDistance", "Silent", "Off"), "Normal")
    private val blocksToEagleValue = IntegerValue("BlocksToEagle", 0, 0, 10)
    private val edgeDistanceValue = FloatValue("EagleEdgeDistance", 0.2f, 0f, 0.5f)

    // Expand
    private val expandLengthValue = IntegerValue("ExpandLength", 1, 1, 6)

    // Rotation Options
    private val rotationStrafeValue = BoolValue("RotationStrafe", false)
    private val rotationModeValue =
        ListValue("RotationMode", arrayOf("Normal", "Static", "StaticPitch", "StaticYaw", "Off"), "Normal")
    private val silentRotationValue = BoolValue("SilentRotation", true)
    private val keepRotationValue = BoolValue("KeepRotation", true)
    private val keepLengthValue = IntegerValue("KeepRotationLength", 0, 0, 20)
    private val staticPitchValue = FloatValue("StaticPitchOffSet", 86f, 70f, 90f)
    private val staticYawValue = FloatValue("StaticYawOffSet", 0f, 0f, 90f)

    // xz + y range
    private val xzRangeValue = FloatValue("xzRange", 0.8f, 0f, 1f)
    private val yRangeValue = FloatValue("yRange", 0.8f, 0f, 1f)

    // Search Accuracy
    /*private val searchAccuracyValue: IntegerValue = object : IntegerValue("SearchAccuracy", 8, 1, 16) {
        override fun onChanged(oldValue: Int, newValue: Int) {
            if (maximum < newValue) {
                set(maximum)
            } else if (minimum > newValue) {
                set(minimum)
            }
        }
    }*/

    // Turn Speed
    private val maxTurnSpeedValue: FloatValue = object : FloatValue("MaxTurnSpeed", 180f, 1f, 180f) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val v = minTurnSpeedValue.get()
            if (v > newValue) set(v)
            if (maximum < newValue) {
                set(maximum)
            } else if (minimum > newValue) {
                set(minimum)
            }
        }
    }
    private val minTurnSpeedValue: FloatValue = object : FloatValue("MinTurnSpeed", 180f, 1f, 180f) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val v = maxTurnSpeedValue.get()
            if (v < newValue) set(v)
            if (maximum < newValue) {
                set(maximum)
            } else if (minimum > newValue) {
                set(minimum)
            }
        }
    }

    // Zitter
    private val zitterValue = BoolValue("Zitter", false)
    private val zitterModeValue = ListValue("ZitterMode", arrayOf("Teleport", "Smooth"), "Teleport")
    private val zitterSpeed = FloatValue("ZitterSpeed", 0.13f, 0.1f, 2f)
    private val zitterStrength = FloatValue("ZitterStrength", 0.05f, 0f, 0.2f)

    // Game
    private val timerValue = FloatValue("Timer", 1f, 0.1f, 10f)
    private val speedModifierValue = FloatValue("SpeedModifier", 1f, 0f, 2f)
    private val slowValue = object : BoolValue("Slow", false) {
        override fun onChanged(oldValue: Boolean, newValue: Boolean) {
            if (newValue) {
                sprintValue.set(false)
            }
        }
    }

    private val slowSpeed = FloatValue("SlowSpeed", 0.6f, 0.2f, 0.8f)

    // Safety
    private val sameYValue = BoolValue("SameY", false)
    private val safeWalkValue = BoolValue("SafeWalk", true)
    private val airSafeValue = BoolValue("AirSafe", false)

    // Visuals
    private val counterDisplayValue = BoolValue("Counter", true)
    private val markValue = BoolValue("Mark", false)

// Module

    // Target block
    private var targetPlace: PlaceInfo? = null

    // Rotation lock
    private var lockRotation: Rotation? = null
    private var limitedRotation: Rotation? = null

    // Launch position
    private var launchY = 0
    private var facesBlock = false

    // AutoBlock
    private var slot = 0

    // Zitter Direction
    private var zitterDirection = false

    // Delay
    private val delayTimer = MSTimer()
    private val zitterTimer = MSTimer()
    private var delay = 0L

    // Eagle
    private var placedBlocksWithoutEagle = 0
    private var eagleSneaking: Boolean = false

    // Downwards
    private var shouldGoDown: Boolean = false

    override fun onEnable() {
        launchY = mc.thePlayer!!.posY.toInt()
    }

    override fun onDisable() {

    }

// Events

    @EventTarget
    private fun onUpdate(event: UpdateEvent) {
        mc.timer.timerSpeed = timerValue.get()
        shouldGoDown =
            downValue.get() && !sameYValue.get() && mc.gameSettings.isKeyDown(mc.gameSettings.keyBindSneak) && blocksAmount > 1

        if (shouldGoDown) {
            mc.gameSettings.keyBindSneak.pressed = false
        }

        if (slowValue.get()) {
            mc.thePlayer!!.motionX = mc.thePlayer!!.motionX * slowSpeed.get()
            mc.thePlayer!!.motionZ = mc.thePlayer!!.motionZ * slowSpeed.get()
        }

        if (sprintValue.get()) {
            if (!mc.gameSettings.isKeyDown(mc.gameSettings.keyBindSprint)) {
                mc.gameSettings.keyBindSprint.pressed = false
            }
            if (mc.gameSettings.isKeyDown(mc.gameSettings.keyBindSprint)) {
                mc.gameSettings.keyBindSprint.pressed = true
            }
            if (mc.gameSettings.keyBindSprint.isKeyDown) {
                mc.thePlayer!!.sprinting = true
            }
            if (!mc.gameSettings.keyBindSprint.isKeyDown) {
                mc.thePlayer!!.sprinting = false
            }
        }
        if (mc.thePlayer!!.onGround) {
            when (modeValue.get().toLowerCase()) {
                "rewinside" -> {
                    MovementUtils.strafe(0.2F)
                    mc.thePlayer!!.motionY = 0.0
                }
            }

            if (zitterValue.get()) {
                when (zitterModeValue.get().toLowerCase()) {
                    "smooth" -> {
                        if (!mc.gameSettings.isKeyDown(mc.gameSettings.keyBindRight)) {
                            mc.gameSettings.keyBindRight.pressed = false
                        }
                        if (!mc.gameSettings.isKeyDown(mc.gameSettings.keyBindLeft)) {
                            mc.gameSettings.keyBindLeft.pressed = false
                        }
                        if (zitterTimer.hasTimePassed(100)) {
                            zitterDirection = !zitterDirection
                            zitterTimer.reset()
                        }
                        if (zitterDirection) {
                            mc.gameSettings.keyBindRight.pressed = true
                            mc.gameSettings.keyBindLeft.pressed = false
                        } else {
                            mc.gameSettings.keyBindRight.pressed = false
                            mc.gameSettings.keyBindLeft.pressed = true
                        }
                    }
                    "teleport" -> {
                        val yaw: Double =
                            Math.toRadians(mc.thePlayer!!.rotationYaw + if (zitterDirection) 90.0 else -90.0)
                        MovementUtils.strafe(zitterSpeed.get())
                        mc.thePlayer!!.motionX = mc.thePlayer!!.motionX - sin(yaw) * zitterStrength.get()
                        mc.thePlayer!!.motionZ = mc.thePlayer!!.motionZ + cos(yaw) * zitterStrength.get()
                        zitterDirection = !zitterDirection
                    }
                }
            }
            // Eagle part for bestnub

        }
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {

    }

    @EventTarget
    fun onStrafe(event: StrafeEvent) {

    }

    @EventTarget
    fun onMotion(event: MotionEvent) {

    }

    // Entity movement event
    @EventTarget
    fun onMove(event: MoveEvent) {
        if (!safeWalkValue.get() || shouldGoDown)
            return
        if (airSafeValue.get() || mc.thePlayer!!.onGround) {
            event.isSafeWalk = true
        }
    }

    // Scaffold visuals 2D
    @EventTarget
    fun onRender2D(event: Render2DEvent) {
        if (counterDisplayValue.get()) {
            GL11.glPushMatrix()
            val blockOverlay = LiquidBounce.moduleManager.getModule(BlockOverlay::class.java) as BlockOverlay
            if (blockOverlay.state && blockOverlay.infoValue.get() && blockOverlay.currentBlock != null) {
                GL11.glTranslatef(0f, 15f, 0f)
            }
            val info = "Blocks: §7$blocksAmount"
            val scaledResolution = classProvider.createScaledResolution(mc)

            RenderUtils.drawBorderedRect(
                scaledResolution.scaledWidth / 2 - 2.toFloat(),
                scaledResolution.scaledHeight / 2 + 5.toFloat(),
                scaledResolution.scaledWidth / 2 + Fonts.font40.getStringWidth(info) + 2.toFloat(),
                scaledResolution.scaledHeight / 2 + 16.toFloat(), 3f, Color.BLACK.rgb, Color.BLACK.rgb
            )

            classProvider.getGlStateManager().resetColor()

            Fonts.font40.drawString(
                info, scaledResolution.scaledWidth / 2.toFloat(),
                scaledResolution.scaledHeight / 2 + 7.toFloat(), Color.WHITE.rgb
            )
            GL11.glPopMatrix()
        }
    }

    // Scaffold visuals 3D
    @EventTarget
    fun onRender3D(event: Render3DEvent) {

    }

// Other functions

    private fun search(blockPosition: WBlockPos): Boolean {
        //Check if block can be (re)placed
        if (!BlockUtils.isReplaceable(blockPosition))
            return false



        return false
    }

    private fun update() {

    }

    private fun findBlock(expand: Boolean) {

    }

    private fun setRotation(rotation: Rotation, keepRotation: Int) {
        if (silentRotationValue.get()) {
            RotationUtils.setTargetRotation(rotation, keepRotation)
        } else {
            mc.thePlayer!!.rotationYaw = rotation.yaw
            mc.thePlayer!!.rotationPitch = rotation.pitch
        }
    }

    private fun setRotation(rotation: Rotation) {
        setRotation(rotation, 0)
    }

    // Return hotbar amount
    private val blocksAmount: Int
        get() {
            var amount = 0
            for (i in 36..44) {
                val itemStack: IItemStack? = mc.thePlayer!!.inventoryContainer.getSlot(i).stack
                if (itemStack != null && classProvider.isItemBlock(itemStack.item)) {
                    val block: IBlock = (itemStack.item!!.asItemBlock()).block
                    val heldItem: IItemStack? = mc.thePlayer!!.heldItem
                    if (heldItem != null && heldItem == itemStack || !InventoryUtils.BLOCK_BLACKLIST.contains(block) && !classProvider.isBlockBush(
                            block
                        )
                    ) {
                        amount += itemStack.stackSize
                    }
                }
            }
            return amount
        }

}