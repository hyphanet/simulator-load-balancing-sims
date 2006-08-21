// A single block of a multi-block response

package messages;

public class Response extends Block
{
	public Response (int id, int index)
	{
		super (id, index);
	}
	
	public String toString()
	{
		return new String ("response (" + id + "," + index + ")");
	}
}
