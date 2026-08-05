function handleMetadatResult(resultData) {
    const metadataContainerElement = jQuery("#metadata-container");
    metadataContainerElement.empty();

    for (const tableName in resultData) {
        let tableHTML = `<div class="metadata-table-wrapper">
                                    <h4>Table: <strong>${tableName}</strong></h4>
                                    <table class="table table-striped">
                                        <thead>
                                            <tr>
                                                <th>Column Name</th>
                                                <th>Type</th>
                                            </tr>
                                        </thead>
                                        <tbody>`;

        resultData[tableName].forEach(column => {
           tableHTML += `<tr>
                            <td>${column.name}</td>
                            <td>${column.type}</td>
                         </tr>`;
        });

        tableHTML += `</tbody></table></div><br>`;
        metadataContainerElement.append(tableHTML);
    }
}

function handleAddStar(event) {
    event.preventDefault();
    const statusMessage = jQuery("#add-star-status-message");

    let formData = jQuery("#add-star-form").serialize();
    formData += "&action=addStar";

    jQuery.ajax({
        url: "../api/employee-dashboard",
        method: "POST",
        data: formData,
        success: (result) => {
            statusMessage.text(result.message)
                         .css("color", result.status === "success" ? "green" : "red");
            if (result.status === "success") jQuery("#add-star-form")[0].reset();
        }
    });
}

function handleAddMovie(event) {
    event.preventDefault();
    const statusMessage = jQuery("#add-movie-status-message");
    let formData = jQuery("#add-movie-form").serialize();
    formData += "&action=addMovie";

    jQuery.ajax({
        url: "../api/employee-dashboard",
        method: "POST",
        data: formData,
        success: (result) => {
            statusMessage.text(result.message)
                         .css("color", result.status === "success" ? "green" : "red");
            if (result.status === "success") $("#add-movie-form")[0].reset();
        }
    });
}

jQuery(document).ready(function() {
   jQuery.ajax({
       url: "../api/employee-dashboard",
       method: "GET",
       success: (result) => handleMetadatResult(result)
   });

   jQuery("#add-star-form").submit(handleAddStar);
   jQuery("#add-movie-form").submit(handleAddMovie);
});