// A single block of a multi-block transfer (currently 32 blocks per transfer)

package messages;

public class Block extends Message
{
	public final int index; // Index of this block from 0-31
	
	public Block (int id, int index)
	{
		this.id = id;
		this.index = index;
	}
	
	public int size()
	{
		return HEADER_SIZE + DATA_SIZE;
	}
	
	public String toString()
	{
		return new String ("block (" + id + "," + index + ")");
	}
}
