// This software has been placed in the public domain by its author

package sim.messages;

public class ChkDataFound extends Message
{
	public ChkDataFound (int id)
	{
		this.id = id;
	}
	
	public String toString()
	{
		return new String ("CHK data found (" + id + ")");
	}
}
