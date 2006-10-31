package sim.messages;

public class SskAccepted extends Message
{
	public final boolean needPubKey;
	
	public SskAccepted (int id, boolean needPubKey)
	{
		this.id = id;
		this.needPubKey = needPubKey;
	}
	
	public String toString()
	{
		return new String ("SSK accepted (" +id+ "," +needPubKey+ ")");
	}
}
