// This software has been placed in the public domain by its author

package sim.messages;
import sim.clients.Client;

public class SskInsert extends Search
{
	public final int data;
	
	// Start a new insert
	public SskInsert (int key, int data, double location, Client client)
	{
		super (key, location, client);
		this.data = data;
	}
	
	// Forward an insert
	public SskInsert (int id, int key, int data, double closest, int htl)
	{
		super (id, key, closest, htl);
		this.data = data;
	}
	
	public int size()
	{
		return HEADER_SIZE + KEY_SIZE + DATA_SIZE;
	}
	
	public String toString()
	{
		return new String ("SSK insert (" +id+ "," +key+ "," +data+")");
	}
}
