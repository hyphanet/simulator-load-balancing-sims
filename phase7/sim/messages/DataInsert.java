// This software has been placed in the public domain by its author

package sim.messages;

public class DataInsert extends Message
{
	public DataInsert (int id)
	{
		this.id = id;
	}
	
	public String toString()
	{
		return new String ("data insert (" + id + ")");
	}
}
