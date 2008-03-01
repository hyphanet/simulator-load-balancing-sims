// This software has been placed in the public domain by its author

package sim.messages;

public class RejectedLoop extends Message
{
	public RejectedLoop (int id)
	{
		this.id = id;
	}
	
	public String toString()
	{
		return new String ("rejected loop (" + id + ")");
	}
}
