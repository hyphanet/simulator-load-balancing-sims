// This software has been placed in the public domain by its author

package sim.messages;

public class Accepted extends Message
{
	public Accepted (int id)
	{
		this.id = id;
	}
	
	public String toString()
	{
		return new String ("accepted (" + id + ")");
	}
}
