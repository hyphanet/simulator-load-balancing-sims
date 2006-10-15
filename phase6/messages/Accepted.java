package messages;

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
