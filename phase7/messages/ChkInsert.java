package messages;

public class ChkInsert extends Search
{
	// Start a new insert
	public ChkInsert (int key, double location)
	{
		super (key, location);
	}
	
	// Forward an insert
	public ChkInsert (int id, int key, double closest, int htl)
	{
		super (id, key, closest, htl);
	}
	
	public String toString()
	{
		return new String ("CHK insert (" + id + "," + key + ")");
	}
}
