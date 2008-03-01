// This software has been placed in the public domain by its author

package sim.messages;

public class RejectedOverload extends Message
{
	public boolean local; // Was this rejection generated locally?
	
	public RejectedOverload (int id, boolean local)
	{
		this.id = id;
		this.local = local;
	}
	
	public String toString()
	{
		return new String ("rejected overload (" +id+ "," +local+ ")");
	}
}
