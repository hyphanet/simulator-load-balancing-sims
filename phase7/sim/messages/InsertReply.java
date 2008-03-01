// This software has been placed in the public domain by its author

package sim.messages;

public class InsertReply extends Message
{
	public InsertReply (int id)
	{
		this.id = id;
	}
	
	public String toString()
	{
		return new String ("insert reply (" + id + ")");
	}
}
