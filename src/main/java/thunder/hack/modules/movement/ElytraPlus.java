package thunder.hack.modules.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.ElytraItem;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import thunder.hack.Thunderhack;
import thunder.hack.cmd.Command;
import thunder.hack.events.impl.EventMove;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.InventoryUtil;
import thunder.hack.utility.MovementUtil;
import thunder.hack.utility.Util;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import static net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_FALL_FLYING;

public class ElytraPlus extends Module {
    public ElytraPlus() {
        super("Elytra+", Category.MOVEMENT);
    }

    private final Setting<Mode> mode = new Setting("Mode", Mode.FireWork);
    private final Setting<Float> xzSpeed = new Setting<>("XZ Speed", 1.9f, 0.5f, 3f); // горизонтальная скорость
    private final Setting<Float> ySpeed = new Setting<>("Y Speed", 0.47f, 0f, 2f); // вертикальная скорость
    private final Setting<Integer> fireSlot = new Setting<>("Firework Slot", 0, 0, 8); // если модуль не найдет фейерверк в хотбаре, то переложит в этот слот
    private final Setting<Float> fireDelay = new Setting<>("Firework Delay", 1.5f, 0f, 1.5f); // интервал использования фейерверков
    private final Setting<Boolean> stayMad = new Setting<>("Stay Off The Ground", true); // не допускать касания земли
    private final Setting<Boolean> keepFlying = new Setting<>("Keep Flying", false); // продолжить лететь если кончились фейерверки (иначе наденется нагрудник и модуль выключится)
    private final Setting<Boolean> bowBomb = new Setting<>("Bow Bomb", false); // усиленная тряска для буста скорости стрел
    private static boolean hasElytra = false;
    private final Setting<Boolean> instantFly = new Setting<>("InstantFly", true);
    public Setting<Boolean> cruiseControl = new Setting<>("CruiseControl", false);
    public Setting<Float> factor = new Setting<>("Factor", 1.5f, 0.1f, 50.0f);
    public Setting<Float> upFactor = new Setting<>("UpFactor", 1.0f, 0.0f, 10.0f);
    public Setting<Float> downFactor = new Setting<>("DownFactor", 1.0f, 0.0f, 10.0f);
    public Setting<Boolean> stopMotion = new Setting<>("StopMotion", true, v -> mode.getValue() == Mode.BOOST);
    public Setting<Float> minUpSpeed = new Setting<>("MinUpSpeed", 0.5f, 0.1f, 5.0f, v -> mode.getValue() == Mode.BOOST && cruiseControl.getValue());
    public Setting<Boolean> forceHeight = new Setting<>("ForceHeight", false, v -> (mode.getValue() == Mode.BOOST && cruiseControl.getValue()));
    private final Setting<Integer> manualHeight = new Setting<>("Height", 121, 1, 256, v -> ((mode.getValue() == Mode.BOOST && cruiseControl.getValue())) && forceHeight.getValue());
    public Setting<Float> speed = new Setting<>("Speed", 1.0f, 0.1f, 10.0f, v -> mode.getValue() == Mode.CONTROL);
    private final Setting<Float> sneakDownSpeed = new Setting<>("DownSpeed", 1.0F, 0.1F, 10.0F, v -> mode.getValue() == Mode.CONTROL);
    private final Setting<Boolean> boostTimer = new Setting<>("Timer", true, v -> mode.getValue() == Mode.BOOST);
    public Setting<Boolean> speedLimit = new Setting<>("SpeedLimit", true);
    public Setting<Float> maxSpeed = new Setting<>("MaxSpeed", 2.5f, 0.1f, 10.0f, v -> speedLimit.getValue());
    public Setting<Boolean> noDrag = new Setting<>("NoDrag", false);
    private final Setting<Float> packetDelay = new Setting<>("Limit", 1F, 0.1F, 5F, v -> mode.getValue() == Mode.BOOST);
    private final Setting<Float> staticDelay = new Setting<>("Delay", 5F, 0.1F, 20F, v -> mode.getValue() == Mode.BOOST);
    private final Setting<Float> timeout = new Setting<>("Timeout", 0.5F, 0.1F, 1F, v -> mode.getValue() == Mode.BOOST);

    private double height;
    private final thunder.hack.utility.Timer instantFlyTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer staticTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer strictTimer = new thunder.hack.utility.Timer();
    private boolean hasTouchedGround = false;
    public enum Mode {FireWork, Sunrise,BOOST,CONTROL}
    private int lastItem = -1;
    private float acceleration;
    private boolean TakeOff, start;

    private int getElytra() {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.ELYTRA && s.getDamage() < 430) {
                return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    private int getFireworks() {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.FIREWORK_ROCKET) {
                return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    @Override
    public void onEnable() {
        if(mode.getValue() == Mode.FireWork) {
            start = true;
            acceleration = 0f;
            if (mc.player.getInventory().getStack(38).getItem() == Items.ELYTRA)
                return;
            int elytra = getElytra();
            if (elytra != -1) {
                lastItem = elytra;
                mc.interactionManager.clickSlot(0, elytra, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(0, 6, 0, SlotActionType.PICKUP, mc.player);
                if (!mc.player.playerScreenHandler.getCursorStack().isEmpty())
                    mc.interactionManager.clickSlot(0, elytra, 0, SlotActionType.PICKUP, mc.player);
            }
        }
        if (mc.player != null) {
            height = mc.player.getY();
            if (!mc.player.isCreative()) mc.player.getAbilities().allowFlying = false;
            mc.player.getAbilities().flying = false;
        }
        hasElytra = false;
    }

    @Override
    public void onDisable() {
        if(mode.getValue() == Mode.FireWork) {
            acceleration = 0f;
            if (keepFlying.getValue())
                return;
            if (lastItem == -1)
                return;
            mc.interactionManager.clickSlot(0, 6, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, this.lastItem, 0, SlotActionType.PICKUP, mc.player);
            if (!mc.player.playerScreenHandler.getCursorStack().isEmpty())
                mc.interactionManager.clickSlot(0, 6, 0, SlotActionType.PICKUP, mc.player);
            lastItem = -1;
        }
        if (mc.player != null) {
            if (!mc.player.isCreative()) mc.player.getAbilities().allowFlying = false;
            mc.player.getAbilities().flying = false;
        }
        Thunderhack.TICK_TIMER = 1.0f;
        hasElytra = false;
    }

    @Override
    public void onUpdate() {
        if (mode.getValue() == Mode.FireWork) {

            if (InventoryUtil.getFireWorks() == -1) {
                int fireworkSlot = getFireworks();
                if (fireworkSlot != -1) {
                    mc.interactionManager.clickSlot(0, fireworkSlot, 0, SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(0, fireSlot.getValue() + 36, 0, SlotActionType.PICKUP, mc.player);
                    if (!mc.player.playerScreenHandler.getCursorStack().isEmpty())
                        mc.interactionManager.clickSlot(0, fireworkSlot, 0, SlotActionType.PICKUP, mc.player);
                    return;
                }
                Command.sendMessage("Нет фейерверков!");
                if (!keepFlying.getValue())
                    this.toggle();
                return;
            }
            if (getElytra() == -1 && mc.player.getInventory().getStack(38).getItem() != Items.ELYTRA) {
                Command.sendMessage("Нет элитр!");
                toggle();
                return;
            }

            if (mc.player.isOnGround()) {
                mc.player.jump();
                TakeOff = true;
                start = true;
            } else if (TakeOff && mc.player.fallDistance > 0.05) {
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, START_FALL_FLYING));
                useFireWork();
                TakeOff = false;
            }
        } else {
            if (fullNullCheck()) return;

            if (mc.player.isOnGround()) {
                hasTouchedGround = true;
            }

            if (!cruiseControl.getValue()) {
                height = mc.player.getY();
            }

            for (ItemStack is : mc.player.getArmorItems()) {
                if (is.getItem() instanceof ElytraItem) {
                    hasElytra = true;
                    break;
                } else {
                    hasElytra = false;
                }
            }

            if (strictTimer.passedMs(1500) && !strictTimer.passedMs(2000)) {
                Thunderhack.TICK_TIMER = 1.0f;
            }

            if (!mc.player.isFallFlying()) {
                if (hasTouchedGround && boostTimer.getValue() && !mc.player.isOnGround()) {
                    Thunderhack.TICK_TIMER = 0.3f;
                }
                if (!mc.player.isOnGround() && instantFly.getValue() && mc.player.getVelocity().getY() < 0D) {
                    if (!instantFlyTimer.passedMs((long) (1000 * timeout.getValue())))
                        return;
                    instantFlyTimer.reset();
                    mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    hasTouchedGround = false;
                    strictTimer.reset();
                }
            }
        }
    }



    @Subscribe
    public void onMove(EventMove e){
        if(mode.getValue() == Mode.FireWork) {

            e.cancel(); // отменяем, для изменения значений
            double motionY = 0; // вводим переменную дельты моушена по Y

            if (mc.player.isFallFlying()) { // если мы летим на элитре
                if (start) { // если стоит флаг старта
                    start = false; // убираем флаг старта
                    useFireWork(); // юзаем фейерверк
                }

                if (mc.player.age % (int) (fireDelay.getValue() * 20) == 0) { // каждые fireDelay * 20 тиков (в целестиале "Задержка фейерверка") ..
                    useFireWork(); // юзаем фейерверк
                }

                if (!MovementUtil.isMoving()) {
                    e.set_x(0);
                    e.set_z(0);
                    acceleration = 0f; // сбрасываем множитель ускорения
                } else { //
                    double[] moveDirection = MovementUtil.forward(lerp(0f, xzSpeed.getValue(), Math.min(acceleration, 1f))); // расчитываем моушены исходя из ускорения и угла поворота камеры
                    e.set_x(moveDirection[0]); // выставляем моушен X
                    e.set_z(moveDirection[1]); // выставляем моушен Z
                    acceleration += 0.1f; // увеличивам множитель ускорения
                }

                if (mc.player.input.jumping) {   // если нажата кнопка прыжка (mc.gameSettings.keyBindJump.isKeyDown() не робит, хз почему)..
                    motionY = ySpeed.getValue(); // дельта будет равна ySpeed (в целестиале "Скорость по Y")
                } else if (mc.options.sneakKey.isPressed()) { // иначе если нажат шифт
                    if (mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(0.0, -1.5, 0.0)).iterator().hasNext() && stayMad.getValue()) // если мы касаемся земли и включен чек "Stay Off The Ground" (в целке "Не приземляться")..
                        motionY = ySpeed.getValue(); // обратно набираем высоту
                    else  // иначе
                        motionY = -ySpeed.getValue(); // опускаемся вниз со скоростью ySpeed (в целестиале "Скорость по Y")
                } else {
                    if (bowBomb.getValue())
                        motionY += mc.player.age % 2 == 0 ? -0.42f : 0.42f;
                    else
                        motionY += mc.player.age % 2 == 0 ? -0.08f : 0.08f;
                }
                e.set_y(motionY);
            }
        } else {
            if (fullNullCheck() || !hasElytra || !mc.player.isFallFlying()) return;

            if (!mc.player.isTouchingWater() || mc.player != null && mc.player.getAbilities().flying && !mc.player.isInLava() || mc.player.getAbilities().flying && mc.player.isFallFlying()) {

                e.cancel();

                if (mode.getValue() != Mode.BOOST) {
                    Vec3d lookVec = mc.player.getRotationVec(mc.getTickDelta());

                    float pitch = mc.player.getPitch() * 0.017453292F;

                    double lookDist = Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z);
                    double motionDist = Math.sqrt(e.get_x() * e.get_x() + e.get_z() * e.get_z());
                    double lookVecDist = lookVec.length();

                    float cosPitch = MathHelper.cos(pitch);
                    cosPitch = (float) ((double) cosPitch * (double) cosPitch * Math.min(1.0D, lookVecDist / 0.4D));

                    if (mode.getValue() != Mode.CONTROL) {
                        e.set_y(e.get_y() + (-0.08D + (double) cosPitch * (0.06D / downFactor.getValue())));
                    }

                    if (mode.getValue() == Mode.CONTROL) {
                        if (mc.options.sneakKey.isPressed()) {
                            e.set_y(-sneakDownSpeed.getValue());
                        } else if (!mc.player.input.jumping) {
                            e.set_y(-0.00000000000003D * downFactor.getValue());
                        }
                    } else if (mode.getValue() != Mode.CONTROL && e.get_y() < 0.0D && lookDist > 0.0D) {
                        double downSpeed = e.get_y() * -0.1D * (double) cosPitch;
                        e.set_y(e.get_y() + downSpeed);
                        e.set_x(e.get_x() + (lookVec.x * downSpeed / lookDist) * factor.getValue());
                        e.set_z(e.get_z() + (lookVec.z * downSpeed / lookDist) * factor.getValue());
                        mc.player.setVelocity(e.get_x(),e.get_y(),e.get_z());

                    }

                    if (pitch < 0.0F && mode.getValue() != Mode.CONTROL) {
                        double rawUpSpeed = motionDist * (double) (-MathHelper.sin(pitch)) * 0.04D;
                        e.set_y(e.get_y() + rawUpSpeed * 3.2D * upFactor.getValue());
                        e.set_x(e.get_x() - lookVec.x * rawUpSpeed / lookDist);
                        e.set_z(e.get_z() - lookVec.z * rawUpSpeed / lookDist);
                        mc.player.setVelocity(e.get_x(),e.get_y(),e.get_z());
                    } else if (mode.getValue() == Mode.CONTROL && mc.player.input.jumping) {
                        if (motionDist > upFactor.getValue() / upFactor.getMax()) {
                            double rawUpSpeed = motionDist * 0.01325D;
                            e.set_y(e.get_y() + rawUpSpeed * 3.2D);
                            e.set_x(e.get_x() - lookVec.x * rawUpSpeed / lookDist);
                            e.set_z(e.get_z() - lookVec.z * rawUpSpeed / lookDist);
                            mc.player.setVelocity(e.get_x(),e.get_y(),e.get_z());
                        } else {
                            double[] dir = MovementUtil.forward(speed.getValue());
                            e.set_x(dir[0]);
                            e.set_z(dir[1]);
                            mc.player.setVelocity(e.get_x(),e.get_y(),e.get_z());
                        }
                    }

                    if (lookDist > 0.0D) {
                        e.set_x(e.get_x() + (lookVec.x / lookDist * motionDist - e.get_x()) * 0.1D);
                        e.set_z(e.get_z() + (lookVec.z / lookDist * motionDist - e.get_z()) * 0.1D);
                        mc.player.setVelocity(e.get_x(),e.get_y(),e.get_z());
                    }

                    if (mode.getValue() == Mode.CONTROL && !mc.player.input.jumping) {
                        double[] dir = MovementUtil.forward(speed.getValue());
                        e.set_x(dir[0]);
                        e.set_z(dir[1]);
                        mc.player.setVelocity(e.get_x(),e.get_y(),e.get_z());
                    }

                    if (!noDrag.getValue()) {
                        e.set_y(e.get_y() * 0.9900000095367432D);
                        e.set_x(e.get_x() * 0.9800000190734863D);
                        e.set_z(e.get_z() * 0.9900000095367432D);
                        mc.player.setVelocity(e.get_x(),e.get_y(),e.get_z());
                    }

                    double finalDist = Math.sqrt(e.get_x() * e.get_x() + e.get_z() * e.get_z());

                    if (speedLimit.getValue() && finalDist > maxSpeed.getValue()) {
                        e.set_x(e.get_x() * maxSpeed.getValue() / finalDist);
                        e.set_z(e.get_z() * maxSpeed.getValue() / finalDist);
                        mc.player.setVelocity(e.get_x(),e.get_y(),e.get_z());
                    }

                } else {
                    float moveForward = mc.player.input.movementForward;

                    if (cruiseControl.getValue()) {
                        if (mc.player.input.jumping) {
                            height += upFactor.getValue() * 0.5;
                        } else if (mc.player.input.sneaking) {
                            height -= downFactor.getValue() * 0.5;
                        }

                        if (forceHeight.getValue()) {
                            height = manualHeight.getValue();
                        }

                        double horizSpeed = Thunderhack.playerManager.currentPlayerSpeed;
                        double horizPct = MathHelper.clamp(horizSpeed / 1.7, 0.0, 1.0);
                        double heightPct = 1 - Math.sqrt(horizPct);
                        double minAngle = 0.6;

                        if (horizSpeed >= minUpSpeed.getValue() && instantFlyTimer.passedMs((long) (2000 * packetDelay.getValue()))) {
                            double pitch = -((45 - minAngle) * heightPct + minAngle);

                            double diff = (height + 1 - mc.player.getY()) * 2;
                            double heightDiffPct = MathHelper.clamp(Math.abs(diff), 0.0, 1.0);
                            double pDist = -Math.toDegrees(Math.atan2(Math.abs(diff), horizSpeed * 30.0)) * Math.signum(diff);

                            double adjustment = (pDist - pitch) * heightDiffPct;

                            mc.player.setPitch((float) pitch);
                            mc.player.setPitch(mc.player.getPitch() + (float) adjustment );
                            mc.player.prevPitch = mc.player.getPitch();
                        } else {
                            mc.player.setPitch(0.25F);
                            mc.player.prevPitch = 0.25F;
                            moveForward = 1F;
                        }
                    }

                    Vec3d vec3d = mc.player.getRotationVec(mc.getTickDelta());

                    float f = mc.player.getPitch() * 0.017453292F;

                    double d6 = Math.sqrt(vec3d.x * vec3d.x + vec3d.z * vec3d.z);
                    double d8 = Math.sqrt(e.get_x() * e.get_x() + e.get_z() * e.get_z());
                    double d1 = vec3d.length();
                    float f4 = MathHelper.cos(f);
                    f4 = (float) ((double) f4 * (double) f4 * Math.min(1.0D, d1 / 0.4D));

                    e.set_y(e.get_y() + (-0.08D + (double) f4 * 0.06D));

                    if (e.get_y() < 0.0D && d6 > 0.0D) {
                        double d2 = e.get_y() * -0.1D * (double) f4;
                        e.set_y(e.get_y() + d2);
                        e.set_x(e.get_x() + vec3d.x * d2 / d6);
                        e.set_z(e.get_z() + vec3d.z * d2 / d6);
                        mc.player.setVelocity(e.get_x(),e.get_y(),e.get_z());
                    }

                    if (f < 0.0F) {
                        double d10 = d8 * (double) (-MathHelper.sin(f)) * 0.04D;
                        e.set_y(e.get_y() + d10 * 3.2D);
                        e.set_x(e.get_x() - vec3d.x * d10 / d6);
                        e.set_z(e.get_z() - vec3d.z * d10 / d6);
                        mc.player.setVelocity(e.get_x(),e.get_y(),e.get_z());
                    }

                    if (d6 > 0.0D) {
                        e.set_x(e.get_x() + (vec3d.x / d6 * d8 - e.get_x()) * 0.1D);
                        e.set_z(e.get_z() + (vec3d.z / d6 * d8 - e.get_z()) * 0.1D);
                        mc.player.setVelocity(e.get_x(),e.get_y(),e.get_z());
                    }

                    if (!noDrag.getValue()) {
                        e.set_y(e.get_y() * 0.9900000095367432D);
                        e.set_x(e.get_x() * 0.9800000190734863D);
                        e.set_z(e.get_z() * 0.9900000095367432D);
                        mc.player.setVelocity(e.get_x(),e.get_y(),e.get_z());
                    }

                    float yaw = mc.player.getYaw() * 0.017453292F;

                    if (f > 0F && e.get_y() < 0D) {
                        if (moveForward != 0F && instantFlyTimer.passedMs((long) (2000 * packetDelay.getValue())) && staticTimer.passedMs((long) (1000 * staticDelay.getValue()))) {
                            if (stopMotion.getValue()) {
                                e.set_x(0);
                                e.set_z(0);
                            }
                            instantFlyTimer.reset();
                            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                        } else if (!instantFlyTimer.passedMs((long) (2000 * packetDelay.getValue()))) {
                            e.set_x(e.get_x() - moveForward * Math.sin(yaw) * factor.getValue() / 20F);
                            e.set_z(e.get_z() + moveForward * Math.cos(yaw) * factor.getValue() / 20F);
                            mc.player.setVelocity(e.get_x(),e.get_y(),e.get_z());
                            staticTimer.reset();
                        }
                    }

                    double finalDist = Math.sqrt(e.get_x() * e.get_x() + e.get_z() * e.get_z());

                    if (speedLimit.getValue() && finalDist > maxSpeed.getValue()) {
                        e.set_x(e.get_x() * maxSpeed.getValue() / finalDist);
                        e.set_z(e.get_z() * maxSpeed.getValue() / finalDist);
                        mc.player.setVelocity(e.get_x(),e.get_y(),e.get_z());
                    }
                }
            }
        }
    }

    public static float lerp(float a, float b, float f) {
        return a + f * (b - a);
    }


    public void useFireWork() {
        int firework_slot = InventoryUtil.getFireWorks();
        if(mc.player.getOffHandStack().getItem() == Items.FIREWORK_ROCKET){
            mc.player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(Hand.OFF_HAND, Util.getWorldActionId(Util.mc.world)));
        } else if(firework_slot != -1) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(firework_slot));
            mc.player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, Util.getWorldActionId(Util.mc.world)));
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
        }
    }

    @Subscribe
    public void onPacketReceive(PacketEvent.Receive e){
        if(e.getPacket() instanceof PlayerPositionLookS2CPacket) acceleration = 0;
    }
}