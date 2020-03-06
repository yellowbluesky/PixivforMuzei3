public class CorruptFileException extends Exception
{
	public CorruptFileException(String message)
	{
		super(message);
	}

	public CorruptFileException(String message, Throwable throwable)
	{
		super(message, throwable);
	}
}