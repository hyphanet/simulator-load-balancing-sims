// A single block of a multi-block transfer (currently 32 blocks per transfer)

package messages;

public class Block extends Message
{
	public final int index; // Index of this block from 0-31
	
	public Block (int id, int index)
	{
		this.id = id;
		this.index = index;
		size = Message.HEADER_SIZE + Message.DATA_SIZE;
	}
}
