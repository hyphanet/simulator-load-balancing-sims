package messages;

public class TransfersCompleted extends Message
{
	public TransfersCompleted (int id)
	{
		this.id = id;
	}
	
	public String toString()
	{
		return new String ("transfers completed (" + id + ")");
	}
}
