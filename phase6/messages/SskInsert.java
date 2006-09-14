package messages;

public class SskInsert extends Search
{
	// Start a new insert
	public SskInsert (int key, double location)
	{
		super (key, location);
	}
	
	// Forward an insert
	public SskInsert (int id, int key, double closest, int htl)
	{
		super (id, key, closest, htl);
	}
	
	public String toString()
	{
		return new String ("SSK insert (" + id + "," + key + ")");
	}
}
