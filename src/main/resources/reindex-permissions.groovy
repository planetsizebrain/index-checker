import com.liferay.portlet.documentlibrary.model.*;
import com.liferay.portal.kernel.search.*;

try {
	String entryClassPK = "192363";
	String className = DLFileEntry.class.getName();
	SearchEngineUtil.updatePermissionFields(className, entryClassPK);

	out.println("Reindexed: " + entryClassPK);
} catch (Exception e) {
	out.println("Failed to reindex: " + entryClassPK);
	e.printStackTrace();
}