document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("[data-refresh]").forEach((button) => {
    button.addEventListener("click", () => window.location.reload());
  });
  document.querySelectorAll("[data-filter-table]").forEach((input) => {
    const table = document.querySelector(input.dataset.filterTable);
    if (!table) return;
    input.addEventListener("input", () => {
      const query = input.value.trim().toLowerCase();
      table.querySelectorAll("tbody tr").forEach((row) => {
        row.hidden = query && !row.textContent.toLowerCase().includes(query);
      });
    });
  });
});
