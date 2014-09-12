package net.famzangl.minecraft.minebot.ai.path;

import java.util.ArrayList;
import java.util.LinkedList;

import net.famzangl.minecraft.minebot.MinebotSettings;
import net.famzangl.minecraft.minebot.Pos;
import net.famzangl.minecraft.minebot.ai.AIHelper;
import net.famzangl.minecraft.minebot.ai.BlockItemFilter;
import net.famzangl.minecraft.minebot.ai.PathFinderField;
import net.famzangl.minecraft.minebot.ai.task.AITask;
import net.famzangl.minecraft.minebot.ai.task.move.AlignToGridTask;
import net.famzangl.minecraft.minebot.ai.task.move.DownwardsMoveTask;
import net.famzangl.minecraft.minebot.ai.task.move.HorizontalMoveTask;
import net.famzangl.minecraft.minebot.ai.task.move.JumpMoveTask;
import net.famzangl.minecraft.minebot.ai.task.move.UpwardsMoveTask;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * A pathfinder that lets you move around a minecraft world.
 * 
 * @author michael
 * 
 */
public class MovePathFinder extends PathFinderField {
	protected Block[] upwardsBuildBlocks = new Block[] { Blocks.dirt,
			Blocks.stone, Blocks.cobblestone };

	protected AIHelper helper;
	protected MinebotSettings settings;

	protected float torchLightLevel;

	/**
	 * Blocks we should not dig through, e.g. because we cannot handle them
	 * correctly.
	 */
	protected static Block[] dangerous = new Block[] { Blocks.bedrock,
			Blocks.cactus, Blocks.obsidian, Blocks.piston_extension,
			Blocks.piston_head };

	private TaskReceiver receiver;

	public MovePathFinder() {
		super();
		settings = new MinebotSettings();

		final ArrayList<Block> blocks = new ArrayList<Block>();
		final String upwardsBuildBlockNames = settings.get(
				"upwards_place_block", "dirt,stone,cobblestone");
		for (final String name : upwardsBuildBlockNames
				.split("\\s*[\\,\\s\\;]\\s*")) {
			final Block block = (Block) Block.blockRegistry.getObject(name);
			if (block != null) {
				blocks.add(block);
			} else {
				System.out.println("Invalid block name: " + name);
			}
		}
		upwardsBuildBlocks = blocks.toArray(new Block[blocks.size()]);
		torchLightLevel = settings.getFloat("place_torches_at", 1.0f, -1, 15);
	}

	@Override
	protected final boolean searchSomethingAround(int cx, int cy, int cz) {
		throw new UnsupportedOperationException("Direct call not supported.");
	}

	protected void addTask(AITask task) {
		receiver.addTask(task);
	}

	public final boolean searchSomethingAround(Pos playerPosition,
			AIHelper helper, TaskReceiver receiver) {
		this.helper = helper;
		this.receiver = receiver;
		return runSearch(playerPosition);
	}

	/**
	 * 
	 * @param playerPosition
	 * @return <code>false</code> When pathfinding should be given more time.
	 */
	protected boolean runSearch(Pos playerPosition) {
		return super.searchSomethingAround(playerPosition.x, playerPosition.y,
				playerPosition.z);
	}

	@Override
	protected int getNeighbour(int currentNode, int cx, int cy, int cz) {
		final int res = super.getNeighbour(currentNode, cx, cy, cz);
		if (res > 0 && !isSafeToTravel(currentNode, cx, cy, cz)) {
			return -1;
		}
		return res;
	}

	protected boolean isForbiddenBlock(Block block) {
		return AIHelper.blockIsOneOf(block, dangerous);
	}

	protected boolean isSafeToTravel(int currentNode, int cx, int cy, int cz) {
		return helper.hasSafeSides(cx, cy + 1, cz)
				&& !isForbiddenBlock(helper.getBlock(cx, cy + 1, cz))
				&& helper.hasSafeSides(cx, cy, cz)
				&& !isForbiddenBlock(helper.getBlock(cx, cy, cz))
				&& checkHeadBlock(currentNode, cx, cy, cz)
				&& checkGroundBlock(currentNode, cx, cy, cz);
	}

	protected boolean checkGroundBlock(int currentNode, int cx, int cy, int cz) {
		if (getY(currentNode) < cy) {
			return helper.isSafeGroundBlock(cx, cy - 1, cz)
					|| helper.canWalkOn(helper.getBlock(cx, cy - 1, cz));
		} else {
			return helper.isSafeGroundBlock(cx, cy - 1, cz);
		}
	}

	private boolean checkHeadBlock(int currentNode, int cx, int cy, int cz) {
		if (getY(currentNode) > cy && helper.isFallingBlock(cx, cy + 2, cz)) {
			// moving down, so ignoring sand, gravel.
			return true;
		}
		return helper.isSafeHeadBlock(cx, cy + 2, cz);
	}

	@Override
	protected void noPathFound() {
		super.noPathFound();
	}

	@Override
	protected void foundPath(LinkedList<Pos> path) {
		super.foundPath(path);
		Pos currentPos = path.removeFirst();
		addTask(new AlignToGridTask(currentPos.x, currentPos.y, currentPos.z));
		while (!path.isEmpty()) {
			Pos nextPos = path.removeFirst();
			final ForgeDirection moveDirection = direction(currentPos, nextPos);

			if (torchLightLevel >= 0 && moveDirection != ForgeDirection.UP) {
				ForgeDirection direction;
				if (moveDirection == ForgeDirection.UP) {
					direction = ForgeDirection.DOWN;
				} else {
					direction = moveDirection;
				}
				addTask(new PlaceTorchIfLightBelowTask(currentPos, direction,
						torchLightLevel));
			}

			final Pos peeked = path.peekFirst();
			if (moveDirection == ForgeDirection.UP && peeked != null
					&& direction(nextPos, peeked).offsetY == 0) {
				// Combine upwards-sidewards.
				// System.out.println("Next direction is: "
				// + direction(nextPos, peeked));
				addTask(new JumpMoveTask(peeked.x, peeked.y, peeked.z,
						nextPos.x, nextPos.z));
				nextPos = peeked;
				path.removeFirst();
			} else if (nextPos.y > currentPos.y) {
				addTask(new UpwardsMoveTask(nextPos.x, nextPos.y, nextPos.z,
						new BlockItemFilter(upwardsBuildBlocks)));
			} else if (nextPos.y < currentPos.y) {
				addTask(new DownwardsMoveTask(nextPos.x, nextPos.y, nextPos.z));
			} else {
				addTask(new HorizontalMoveTask(nextPos.x, nextPos.y, nextPos.z));
			}
			currentPos = nextPos;
		}
		addTasksForTarget(currentPos);
	}

	private ForgeDirection direction(Pos currentPos, final Pos nextPos) {
		return AIHelper.getDirectionFor(nextPos.subtract(currentPos));
	}

	protected void addTasksForTarget(Pos currentPos) {
	}

	@Override
	protected int distanceFor(int from, int to) {
		int distance = 0;
		// if (getY(from) != getY(to)) {
		// up or down
		distance += 1;
		// } else {
		// distance += 3;
		// }
		if (getY(from) >= getY(to)) {
			distance += materialDistance(getX(to), getY(to), getZ(to), true);
		}
		if (getY(from) <= getY(to)) {
			distance += materialDistance(getX(to), getY(to) + 1, getZ(to),
					false);
		}
		return distance;
	}

	protected int materialDistance(int x, int y, int z, boolean asFloor) {
		final Block block = helper.getBlock(x, y, z);
		if (Block.isEqualTo(block, Blocks.air) || asFloor
				&& helper.canWalkOn(block) || !asFloor
				&& helper.canWalkThrough(block)) {
			return 0;
		} else if (AIHelper.blockIsOneOf(block, Blocks.dirt, Blocks.gravel,
				Blocks.sand, Blocks.sandstone)) {
			// fast breaking gives bonus.
			return 1;
		} else {
			return 2;
		}
	}

}
