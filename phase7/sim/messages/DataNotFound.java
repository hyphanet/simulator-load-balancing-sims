// This software has been placed in the public domain by its author

package sim.messages;

public class DataNotFound extends Message
{
	public DataNotFound (int id)
	{
		this.id = id;
	}
	
	public String toString()
	{
		return new String ("data not found (" + id + ")");
	}
}
