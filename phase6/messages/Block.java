// A single block of a data transfer (currently 32 blocks per transfer)

package messages;

public class Block extends Message
{
	public final static int SIZE = 1024; // Bytes
	
	// FIXME: placeholder
	public Block()
	{
		size = SIZE;
	}
}
