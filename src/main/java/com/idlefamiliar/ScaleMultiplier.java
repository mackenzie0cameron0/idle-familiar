package com.idlefamiliar;

public enum ScaleMultiplier
{
	X1("x1", 1.0),
	X2("x2", 2.0),
	X3("x3", 3.0);

	private final String label;
	private final double multiplier;

	ScaleMultiplier(String label, double multiplier)
	{
		this.label = label;
		this.multiplier = multiplier;
	}

	public double multiplier()
	{
		return multiplier;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
