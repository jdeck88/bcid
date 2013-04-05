<%@ include file="header.jsp" %>

<div id="resolver" class="section">
    <h2>Resolver</h2>

    <div class="sectioncontent">
        Resolves Biocode Commons Identifiers and EZIDs (e.g. ark:/87286/C2)

        <form>
        <table border=0>
            <tr>
                <td align=right>Identifier</td>
                <td align=left><input type=text name="identifier" id="identifier" size="40"></td>
            </tr>
            <tr>
                <td colspan=2><input type="button" onclick="resolverResults('resolverResults');" name="Submit" value="Submit" /></td>
            </tr>
         </table>
         </form>
    </div>

    <div id="resolverResults" style="overflow:auto;">
    </div>
</div>


<%@ include file="footer.jsp" %>


