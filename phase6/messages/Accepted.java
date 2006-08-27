package messages;

public class Accepted extends Message
{
	public Accepted (int id)
	{
		this.id = id;
		size = Message.HEADER_SIZE;
	}
	
	public String toString()
	{
		return new String ("accepted (" + id + ")");
	}
}
