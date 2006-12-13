package sim.messages;

public class SskRequest extends Search
{
	public final boolean needPubKey;
	
	// Start a new request
	public SskRequest (int key, double location, boolean needPubKey)
	{
		super (key, location, null);
		this.needPubKey = needPubKey;
	}
	
	// Forward a request
	public SskRequest (int id, int key, double closest,
				int htl, boolean needPubKey)
	{
		super (id, key, closest, htl);
		this.needPubKey = needPubKey;
	}
	
	public String toString()
	{
		return new String ("SSK request (" + id + "," + key
					+ "," + needPubKey + ")");
	}
}
