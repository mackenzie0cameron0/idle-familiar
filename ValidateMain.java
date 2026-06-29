import com.idlefamiliar.AvatarAssetValidator;
import java.io.File;
import java.util.List;

public class ValidateMain
{
	public static void main(String[] args)
	{
		List<String> issues = AvatarAssetValidator.validate(new File(args[0]));
		System.out.println("issues=" + issues.size());
		for (String s : issues)
		{
			System.out.println(" - " + s);
		}
	}
}
