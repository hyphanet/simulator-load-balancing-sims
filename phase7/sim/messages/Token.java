package sim.messages;

public class Token extends Message
{
	public Token (int tokens)
	{
		id = tokens; // Space-saving hack
	}
	
	public String toString()
	{
		return new String (id + " tokens");
	}
}
