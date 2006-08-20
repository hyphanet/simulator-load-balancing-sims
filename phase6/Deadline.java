// A queued item and the time at which it must be sent

class Deadline<Item>
{
	public final Item item;
	public final double deadline;
	
	public Deadline (Item item, double deadline)
	{
		this.item = item;
		this.deadline = deadline;
	}
}
