package messages;

public class SskAccepted extends Message
{
	public final boolean needPubKey;
	
	public SskAccepted (int id, boolean needPubKey)
	{
		this.id = id;
		this.needPubKey = needPubKey;
		size = Message.HEADER_SIZE;
	}
	
	public String toString()
	{
		return new String ("SSK accepted (" +id+ "," +needPubKey+ ")");
	}
}
