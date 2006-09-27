package messages;

public class SskInsert extends Search
{
	public final int data;
	
	// Start a new insert
	public SskInsert (int key, int data, double location)
	{
		super (key, location);
		this.data = data;
		size += DATA_SIZE;
	}
	
	// Forward an insert
	public SskInsert (int id, int key, int data, double closest, int htl)
	{
		super (id, key, closest, htl);
		this.data = data;
		size += DATA_SIZE;
	}
	
	public String toString()
	{
		return new String ("SSK insert (" +id+ "," +key+ "," +data+")");
	}
}
