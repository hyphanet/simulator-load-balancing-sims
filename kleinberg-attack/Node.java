import java.util.HashSet;

class Node
{
	private HashSet<Node> neighbours;
	private double location;
	
	public Node (double location)
	{
		neighbours = new HashSet<Node>();
		this.location = location;
	}
	
	public void connect (Node n)
	{
		if (n == this || neighbours.contains (n)) return;
		neighbours.add (n);
	}
	
	public void disconnect (Node n)
	{
		neighbours.remove (this);
	}
	
	public void die()
	{
		for (Node n : neighbours) n.disconnect (this);
		neighbours.clear();
	}
	
	// Return the length of the longest link
	public double longestDistance()
	{
		double best = 0.0;
		for (Node n : neighbours) {
			double d = distance (location, n.location);
			if (d > best) best = d;
		}
		return best;
	}
	
	// Return the total length of all links
	public double totalDistance()
	{
		double d = 0.0;
		for (Node n : neighbours) d += distance (location, n.location);
		return d;
	}
	
	public Node route (Node dest)
	{
		if (dest == this) return null;
		Node bestNeighbour = null;
		double bestDistance = Double.POSITIVE_INFINITY;
		for (Node n : neighbours) {
			double d = distance (n.location, dest.location);
			if (d < bestDistance) {
				bestNeighbour = n;
				bestDistance = d;
			}
		}
		if (bestDistance < distance (location, dest.location))
			return bestNeighbour;
		else return null;
	}
	
	// Return the circular distance between two locations
	public static double distance (double a, double b)
	{
		if (a > b) return Math.min (a - b, b - a + 1.0);
		else return Math.min (b - a, a - b + 1.0);
	}
}
