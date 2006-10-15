package messages;

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
