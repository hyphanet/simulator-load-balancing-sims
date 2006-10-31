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
