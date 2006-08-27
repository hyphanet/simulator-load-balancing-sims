package messages;

public class RejectedLoop extends Message
{
	public RejectedLoop (int id)
	{
		this.id = id;
		size = Message.HEADER_SIZE;
	}
	
	public String toString()
	{
		return new String ("rejected loop (" + id + ")");
	}
}
