let observer = new MutationObserver(() => {
  // find the logout button
  const logoutButton = document.querySelectorAll(".logout > a")[0];
  // once the button is present add the event listener
  if (logoutButton) {
    logoutButton.addEventListener("click", () => {
      // do whatever you want to do on logout
      window.location.href = "logout";
    });
    observer.disconnect();
  }
});

observer.observe(document.body, {
  childList: true,
  subtree: true,
  attributes: false,
  characterData: false
});