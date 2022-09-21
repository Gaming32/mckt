package io.github.gaming32.mckt.objects

enum class Axis(val unit: BlockPosition, private val opposite: BlockPosition) {
    X(BlockPosition.EAST, BlockPosition.WEST),
    Y(BlockPosition.UP, BlockPosition.DOWN),
    Z(BlockPosition.SOUTH, BlockPosition.NORTH);

    fun direction(direction: Int) = if (direction < 0) opposite else unit
}