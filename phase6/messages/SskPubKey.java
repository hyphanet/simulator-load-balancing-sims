package messages;

public class SskPubKey extends Message
{
	public final int key;
	
	public SskPubKey (int id, int key)
	{
		this.id = id;
		this.key = key;
		size = Message.HEADER_SIZE + Message.PUB_KEY_SIZE;
	}
	
	public String toString()
	{
		return new String ("SSK public key (" + id + "," + key + ")");
	}
}
