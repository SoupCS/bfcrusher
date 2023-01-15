package ru.justnanix.bfcrusher.bot.entity;

import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCommandBlock;
import net.minecraft.block.BlockStructure;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.*;
import net.minecraft.stats.RecipeBook;
import net.minecraft.stats.StatisticsManager;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import ru.justnanix.bfcrusher.bot.network.BPlayHandler;

public class BPlayerController {
    private final BPlayHandler connection;
    private BlockPos currentBlock = new BlockPos(-1, -1, -1);
    private ItemStack currentItemHittingBlock = ItemStack.EMPTY;
    
    private float curBlockDamageMP;
    private int blockHitDelay;
    private boolean isHittingBlock;
    
    private GameType currentGameType = GameType.SURVIVAL;
    private int currentPlayerItem;

    public BPlayerController(BPlayHandler netHandler) {
        this.connection = netHandler;
    }

    public void clickBlockCreative(BlockPos pos, EnumFacing facing) {
        if (!connection.getWorld().extinguishFire(connection.getBot(), pos, facing)) {
            onPlayerDestroyBlock(pos);
        }
    }

    /**
     * Sets player capabilities depending on current gametype. params: player
     */
    public void setPlayerCapabilities(EntityPlayer player) {
        this.currentGameType.configurePlayerCapabilities(player.abilities);
    }

    public boolean isSpectator() {
        return this.currentGameType == GameType.SPECTATOR;
    }

    /**
     * Sets the game type for the player.
     */
    public void setGameType(GameType type) {
        this.currentGameType = type;
        this.currentGameType.configurePlayerCapabilities(this.connection.getBot().abilities);
    }

    public void flipPlayer(EntityPlayer player) {
        player.rotationYaw = -180.0F;
    }

    public boolean shouldDrawHUD() {
        return this.currentGameType.isSurvivalOrAdventure();
    }

    public boolean onPlayerDestroyBlock(BlockPos pos) {
        if (this.currentGameType.hasLimitedInteractions()) {
            if (this.currentGameType == GameType.SPECTATOR) {
                return false;
            }

            if (!this.connection.getBot().isAllowEdit()) {
                ItemStack itemstack = this.connection.getBot().getHeldItemMainhand();

                if (itemstack.isEmpty()) {
                    return false;
                }

                if (!itemstack.func_179544_c(this.connection.getWorld().getBlockState(pos).getBlock())) {
                    return false;
                }
            }
        }

        if (this.currentGameType.isCreative() && !this.connection.getBot().getHeldItemMainhand().isEmpty() && this.connection.getBot().getHeldItemMainhand().getItem() instanceof ItemSword) {
            return false;
        } else {
            World world = this.connection.getWorld();
            IBlockState iblockstate = world.getBlockState(pos);
            Block block = iblockstate.getBlock();

            if ((block instanceof BlockCommandBlock || block instanceof BlockStructure) && !this.connection.getBot().func_189808_dh()) {
                return false;
            } else if (iblockstate.getMaterial() == Material.AIR) {
                return false;
            } else {
                world.func_175718_b(2001, pos, Block.func_176210_f(iblockstate));
                block.onBlockHarvested(world, pos, iblockstate, this.connection.getBot());
                boolean flag = world.setBlockState(pos, Blocks.AIR.getDefaultState(), 11);

                if (flag) {
                    block.onPlayerDestroy(world, pos, iblockstate);
                }

                this.currentBlock = new BlockPos(this.currentBlock.getX(), -1, this.currentBlock.getZ());

                if (!this.currentGameType.isCreative()) {
                    ItemStack itemstack1 = this.connection.getBot().getHeldItemMainhand();

                    if (!itemstack1.isEmpty()) {
                        itemstack1.onBlockDestroyed(world, iblockstate, pos, this.connection.getBot());

                        if (itemstack1.isEmpty()) {
                            this.connection.getBot().setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
                        }
                    }
                }

                return flag;
            }
        }
    }

    /**
     * Called when the player is hitting a block with an item.
     */
    public boolean clickBlock(BlockPos loc, EnumFacing face) {
        if (this.currentGameType.hasLimitedInteractions()) {
            if (this.currentGameType == GameType.SPECTATOR) {
                return false;
            }

            if (!this.connection.getBot().isAllowEdit()) {
                ItemStack itemstack = this.connection.getBot().getHeldItemMainhand();

                if (itemstack.isEmpty()) {
                    return false;
                }

                if (!itemstack.func_179544_c(this.connection.getWorld().getBlockState(loc).getBlock())) {
                    return false;
                }
            }
        }

        if (!this.connection.getWorld().getWorldBorder().contains(loc)) {
            return false;
        } else {
            if (this.currentGameType.isCreative()) {
                this.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, loc, face));
                clickBlockCreative(loc, face);
                this.blockHitDelay = 5;
            } else if (!this.isHittingBlock || !this.isHittingPosition(loc)) {
                if (this.isHittingBlock) {
                    this.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, this.currentBlock, face));
                }

                IBlockState iblockstate = this.connection.getWorld().getBlockState(loc);
                this.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, loc, face));
                boolean flag = iblockstate.getMaterial() != Material.AIR;

                if (flag && this.curBlockDamageMP == 0.0F) {
                    iblockstate.getBlock().func_180649_a(this.connection.getWorld(), loc, this.connection.getBot());
                }

                if (flag && iblockstate.getPlayerRelativeBlockHardness(this.connection.getBot(), this.connection.getBot().world, loc) >= 1.0F) {
                    this.onPlayerDestroyBlock(loc);
                } else {
                    this.isHittingBlock = true;
                    this.currentBlock = loc;
                    this.currentItemHittingBlock = this.connection.getBot().getHeldItemMainhand();
                    this.curBlockDamageMP = 0.0F;
                    this.connection.getWorld().sendBlockBreakProgress(this.connection.getBot().getEntityId(), this.currentBlock, (int) (this.curBlockDamageMP * 10.0F) - 1);
                }
            }

            return true;
        }
    }

    /**
     * Resets current block damage
     */
    public void resetBlockRemoving() {
        if (this.isHittingBlock) {
            this.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, this.currentBlock, EnumFacing.DOWN));
            this.isHittingBlock = false;
            this.curBlockDamageMP = 0.0F;
            this.connection.getWorld().sendBlockBreakProgress(this.connection.getBot().getEntityId(), this.currentBlock, -1);
            this.connection.getBot().resetCooldown();
        }
    }

    public boolean onPlayerDamageBlock(BlockPos posBlock, EnumFacing directionFacing) {
        this.syncCurrentPlayItem();

        if (this.blockHitDelay > 0) {
            --this.blockHitDelay;
            return true;
        } else if (this.currentGameType.isCreative() && this.connection.getWorld().getWorldBorder().contains(posBlock)) {
            this.blockHitDelay = 5;
            this.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, posBlock, directionFacing));
            clickBlockCreative(posBlock, directionFacing);
            return true;
        } else if (this.isHittingPosition(posBlock)) {
            IBlockState iblockstate = this.connection.getWorld().getBlockState(posBlock);
            Block block = iblockstate.getBlock();

            if (iblockstate.getMaterial() == Material.AIR) {
                this.isHittingBlock = false;
                return false;
            } else {
                this.curBlockDamageMP += iblockstate.getPlayerRelativeBlockHardness(this.connection.getBot(), this.connection.getBot().world, posBlock);

                if (this.curBlockDamageMP >= 1.0F) {
                    this.isHittingBlock = false;
                    this.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, posBlock, directionFacing));
                    this.onPlayerDestroyBlock(posBlock);
                    this.curBlockDamageMP = 0.0F;
                    this.blockHitDelay = 5;
                }

                this.connection.getWorld().sendBlockBreakProgress(this.connection.getBot().getEntityId(), this.currentBlock, (int) (this.curBlockDamageMP * 10.0F) - 1);
                return true;
            }
        } else {
            return this.clickBlock(posBlock, directionFacing);
        }
    }

    /**
     * player reach distance = 4F
     */
    public float getBlockReachDistance() {
        return this.currentGameType.isCreative() ? 5.0F : 4.5F;
    }

    public void tick() {
        this.syncCurrentPlayItem();

        if (this.connection.getNetworkManager().isChannelOpen()) {
            this.connection.getNetworkManager().tick();
        } else {
            this.connection.getNetworkManager().handleDisconnection();
        }
    }

    private boolean isHittingPosition(BlockPos pos) {
        ItemStack itemstack = this.connection.getBot().getHeldItemMainhand();
        boolean flag = this.currentItemHittingBlock.isEmpty() && itemstack.isEmpty();

        if (!this.currentItemHittingBlock.isEmpty() && !itemstack.isEmpty()) {
            flag = itemstack.getItem() == this.currentItemHittingBlock.getItem() && ItemStack.areItemStackTagsEqual(itemstack, this.currentItemHittingBlock) && (itemstack.isDamageable() || itemstack.func_77960_j() == this.currentItemHittingBlock.func_77960_j());
        }

        return pos.equals(this.currentBlock) && flag;
    }

    /**
     * Syncs the current player item with the server
     */
    private void syncCurrentPlayItem() {
        int i = this.connection.getBot().inventory.currentItem;

        if (i != this.currentPlayerItem) {
            this.currentPlayerItem = i;
            this.connection.sendPacket(new CPacketHeldItemChange(this.currentPlayerItem));
        }
    }

    public EnumActionResult func_187099_a(EntityPlayerSP p_187099_1_, WorldClient p_187099_2_, BlockPos p_187099_3_, EnumFacing p_187099_4_, Vec3d p_187099_5_, EnumHand p_187099_6_) {
        this.syncCurrentPlayItem();
        ItemStack itemstack = p_187099_1_.getHeldItem(p_187099_6_);
        float f = (float) (p_187099_5_.x - (double) p_187099_3_.getX());
        float f1 = (float) (p_187099_5_.y - (double) p_187099_3_.getY());
        float f2 = (float) (p_187099_5_.z - (double) p_187099_3_.getZ());
        boolean flag = false;

        if (!this.connection.getWorld().getWorldBorder().contains(p_187099_3_)) {
            return EnumActionResult.FAIL;
        } else {
            if (this.currentGameType != GameType.SPECTATOR) {
                IBlockState iblockstate = p_187099_2_.getBlockState(p_187099_3_);

                if ((!p_187099_1_.func_70093_af() || p_187099_1_.getHeldItemMainhand().isEmpty() && p_187099_1_.getHeldItemOffhand().isEmpty()) && iblockstate.getBlock().func_180639_a(p_187099_2_, p_187099_3_, iblockstate, p_187099_1_, p_187099_6_, p_187099_4_, f, f1, f2)) {
                    flag = true;
                }

                if (!flag && itemstack.getItem() instanceof ItemBlock) {
                    ItemBlock itemblock = (ItemBlock) itemstack.getItem();

                    if (!itemblock.func_179222_a(p_187099_2_, p_187099_3_, p_187099_4_, p_187099_1_, itemstack)) {
                        return EnumActionResult.FAIL;
                    }
                }
            }

            this.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(p_187099_3_, p_187099_4_, p_187099_6_, f, f1, f2));

            if (!flag && this.currentGameType != GameType.SPECTATOR) {
                if (itemstack.isEmpty()) {
                    return EnumActionResult.PASS;
                } else if (p_187099_1_.getCooldownTracker().hasCooldown(itemstack.getItem())) {
                    return EnumActionResult.PASS;
                } else {
                    if (itemstack.getItem() instanceof ItemBlock && !p_187099_1_.func_189808_dh()) {
                        Block block = ((ItemBlock) itemstack.getItem()).getBlock();

                        if (block instanceof BlockCommandBlock || block instanceof BlockStructure) {
                            return EnumActionResult.FAIL;
                        }
                    }

                    if (this.currentGameType.isCreative()) {
                        int i = itemstack.func_77960_j();
                        int j = itemstack.getCount();
                        EnumActionResult enumactionresult = itemstack.func_179546_a(p_187099_1_, p_187099_2_, p_187099_3_, p_187099_6_, p_187099_4_, f, f1, f2);
                        itemstack.func_77964_b(i);
                        itemstack.setCount(j);
                        return enumactionresult;
                    } else {
                        return itemstack.func_179546_a(p_187099_1_, p_187099_2_, p_187099_3_, p_187099_6_, p_187099_4_, f, f1, f2);
                    }
                }
            } else {
                return EnumActionResult.SUCCESS;
            }
        }
    }

    public EnumActionResult processRightClick(EntityPlayer player, World worldIn, EnumHand hand) {
        if (this.currentGameType == GameType.SPECTATOR) {
            return EnumActionResult.PASS;
        } else {
            this.syncCurrentPlayItem();
            this.connection.sendPacket(new CPacketPlayerTryUseItem(hand));
            ItemStack itemstack = player.getHeldItem(hand);

            if (player.getCooldownTracker().hasCooldown(itemstack.getItem())) {
                return EnumActionResult.PASS;
            } else {
                int i = itemstack.getCount();
                ActionResult<ItemStack> actionresult = itemstack.useItemRightClick(worldIn, player, hand);
                ItemStack itemstack1 = actionresult.getResult();

                if (itemstack1 != itemstack || itemstack1.getCount() != i) {
                    player.setHeldItem(hand, itemstack1);
                }

                return actionresult.getType();
            }
        }
    }

    /**
     * Attacks an entity
     */
    public void attackEntity(EntityPlayer playerIn, Entity targetEntity) {
        this.syncCurrentPlayItem();
        this.connection.sendPacket(new CPacketUseEntity(targetEntity));

        if (this.currentGameType != GameType.SPECTATOR) {
            playerIn.attackTargetEntityWithCurrentItem(targetEntity);
            playerIn.resetCooldown();
        }
    }

    /**
     * Handles right clicking an entity, sends a packet to the server.
     */
    public EnumActionResult interactWithEntity(EntityPlayer player, Entity target, EnumHand hand) {
        this.syncCurrentPlayItem();
        this.connection.sendPacket(new CPacketUseEntity(target, hand));
        return this.currentGameType == GameType.SPECTATOR ? EnumActionResult.PASS : player.interactOn(target, hand);
    }

    /**
     * Handles right clicking an entity from the entities side, sends a packet to the server.
     */
    public EnumActionResult interactWithEntity(EntityPlayer player, Entity target, RayTraceResult ray, EnumHand hand) {
        this.syncCurrentPlayItem();
        Vec3d vec3d = new Vec3d(ray.hitResult.x - target.posX, ray.hitResult.y - target.posY, ray.hitResult.z - target.posZ);
        this.connection.sendPacket(new CPacketUseEntity(target, hand, vec3d));
        return this.currentGameType == GameType.SPECTATOR ? EnumActionResult.PASS : target.applyPlayerInteraction(player, vec3d, hand);
    }

    /**
     * Handles slot clicks, sends a packet to the server.
     */
    public ItemStack windowClick(int windowId, int slotId, int mouseButton, ClickType type, EntityPlayer player) {
        short short1 = player.openContainer.getNextTransactionID(player.inventory);
        ItemStack itemstack = player.openContainer.slotClick(slotId, mouseButton, type, player);
        this.connection.sendPacket(new CPacketClickWindow(windowId, slotId, mouseButton, type, itemstack, short1));
        return itemstack;
    }

    public void func_194338_a(int p_194338_1_, IRecipe p_194338_2_, boolean p_194338_3_, EntityPlayer p_194338_4_) {
        this.connection.sendPacket(new CPacketPlaceRecipe(p_194338_1_, p_194338_2_, p_194338_3_));
    }

    /**
     * GuiEnchantment uses this during multiplayer to tell PlayerControllerMP to send a packet indicating the
     * enchantment action the player has taken.
     */
    public void sendEnchantPacket(int windowID, int button) {
        this.connection.sendPacket(new CPacketEnchantItem(windowID, button));
    }

    /**
     * Used in PlayerControllerMP to update the server with an ItemStack in a slot.
     */
    public void sendSlotPacket(ItemStack itemStackIn, int slotId) {
        if (this.currentGameType.isCreative()) {
            this.connection.sendPacket(new CPacketCreativeInventoryAction(slotId, itemStackIn));
        }
    }

    /**
     * Sends a Packet107 to the server to drop the item on the ground
     */
    public void sendPacketDropItem(ItemStack itemStackIn) {
        if (this.currentGameType.isCreative() && !itemStackIn.isEmpty()) {
            this.connection.sendPacket(new CPacketCreativeInventoryAction(-1, itemStackIn));
        }
    }

    public void onStoppedUsingItem(EntityPlayer playerIn) {
        this.syncCurrentPlayItem();
        this.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ZERO, EnumFacing.DOWN));
        playerIn.stopActiveHand();
    }

    public boolean gameIsSurvivalOrAdventure() {
        return this.currentGameType.isSurvivalOrAdventure();
    }

    /**
     * Checks if the player is not creative, used for checking if it should break a block instantly
     */
    public boolean isNotCreative() {
        return !this.currentGameType.isCreative();
    }

    /**
     * returns true if player is in creative mode
     */
    public boolean isInCreativeMode() {
        return this.currentGameType.isCreative();
    }

    /**
     * true for hitting entities far away.
     */
    public boolean extendedReach() {
        return this.currentGameType.isCreative();
    }

    /**
     * Checks if the player is riding a horse, used to chose the GUI to open
     */
    public boolean isRidingHorse() {
        return this.connection.getBot().isPassenger() && this.connection.getBot().getRidingEntity() instanceof AbstractHorse;
    }

    public boolean isSpectatorMode() {
        return this.currentGameType == GameType.SPECTATOR;
    }

    public GameType getCurrentGameType() {
        return this.currentGameType;
    }

    /**
     * Return isHittingBlock
     */
    public boolean getIsHittingBlock() {
        return this.isHittingBlock;
    }

    public void pickItem(int index) {
        this.connection.sendPacket(new CPacketCustomPayload("MC|PickItem", (new PacketBuffer(Unpooled.buffer())).writeVarInt(index)));
    }
}
