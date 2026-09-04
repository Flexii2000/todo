// Das Brett: Bereiche als Kacheln, Aufgaben mit Unteraufgaben. Jede Antwort
// des Dienstes ist das ganze Brett - hier wird nur gezeichnet, nie gerechnet.
(function () {
  "use strict";

  const boardEl = document.getElementById("board");
  const banner = document.getElementById("banner");
  const stamp = document.getElementById("stamp");
  const archive = document.getElementById("archive");
  let showAll = false;

  async function call(method, path, body) {
    const res = await fetch("api/" + path, {
      method,
      headers: body ? { "Content-Type": "application/json" } : {},
      body: body ? JSON.stringify(body) : undefined,
      credentials: "same-origin",
    });
    if (!res.ok) {
      // Fehler kommen als Klartext ("Eine Aufgabe braucht einen Text.").
      throw new Error((await res.text()) || "HTTP " + res.status);
    }
    return res.json();
  }

  function fail(err) {
    banner.textContent = err.message;
    banner.hidden = false;
  }

  async function run(method, path, body) {
    try {
      banner.hidden = true;
      render(await call(method, path, body));
    } catch (err) { fail(err); }
  }

  const load = () => run("GET", "board" + (showAll ? "?all=true" : ""));

  function fmtDate(iso) {
    const d = new Date(iso);
    return d.toLocaleDateString("de-DE", { day: "2-digit", month: "2-digit" });
  }

  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  function addForm(placeholder, onSubmit, cls) {
    const form = el("form", "add" + (cls ? " " + cls : ""));
    const input = el("input");
    input.placeholder = placeholder;
    input.maxLength = 200;
    const button = el("button", null, "+");
    form.append(input, button);
    form.addEventListener("submit", (ev) => {
      ev.preventDefault();
      const value = input.value.trim();
      if (!value) return;
      input.value = "";
      onSubmit(value);
    });
    return form;
  }

  function todoItem(area, todo, isChild) {
    const li = el("li", "todo" + (todo.doneAt ? " done" : ""));
    const box = el("input");
    box.type = "checkbox";
    box.checked = !!todo.doneAt;
    box.title = todo.doneAt ? "Wieder öffnen" : "Erledigt";
    box.addEventListener("change", () =>
      run(box.checked ? "POST" : "DELETE", "todos/" + todo.id + "/done"));
    const title = el("span", "title", todo.title);
    if (todo.doneAt) {
      // Sagen, wann sie verschwindet - sonst wundert man sich am vierten Tag.
      title.append(el("span", "until", "bis " + fmtDate(todo.visibleUntil)));
    }
    li.append(box, title);
    if (!isChild && !todo.doneAt) {
      const sub = el("button", "sub", "+ Unteraufgabe");
      sub.type = "button";
      sub.addEventListener("click", () => {
        if (li.nextSibling && li.nextSibling.classList && li.nextSibling.classList.contains("sub-add")) {
          li.nextSibling.remove();
          return;
        }
        const form = addForm("Unteraufgabe", (text) =>
          run("POST", "todos", { areaId: area.id, parentId: todo.id, title: text }), "sub-add");
        li.after(form);
        form.querySelector("input").focus();
      });
      li.append(sub);
    }
    const x = el("button", "x", "×");
    x.type = "button";
    x.title = "Löschen - wirklich, nicht nur ausblenden";
    x.addEventListener("click", () => {
      if (confirm("„" + todo.title + "“ endgültig löschen?")) run("DELETE", "todos/" + todo.id);
    });
    li.append(x);
    return li;
  }

  function areaTile(area) {
    const tile = el("section", "area");
    const h2 = el("h2");
    h2.append(el("span", null, area.name), el("span", "count", area.openCount + " offen"));
    const tools = el("span", "tools");
    const rename = el("button", "x", "✎");
    rename.type = "button"; rename.title = "Umbenennen";
    rename.addEventListener("click", () => {
      const name = prompt("Neuer Name für den Bereich:", area.name);
      if (name && name.trim() && name.trim() !== area.name) run("PUT", "areas/" + area.id, { name: name.trim() });
    });
    const del = el("button", "x", "×");
    del.type = "button"; del.title = "Bereich löschen";
    del.addEventListener("click", () => {
      if (confirm("Bereich „" + area.name + "“ mit allen Aufgaben löschen?")) run("DELETE", "areas/" + area.id);
    });
    tools.append(rename, del);
    h2.append(tools);
    tile.append(h2);

    const list = el("ul", "todos");
    for (const todo of area.todos) {
      list.append(todoItem(area, todo, false));
      if (todo.children.length) {
        const sub = el("ul", "todos");
        for (const child of todo.children) sub.append(todoItem(area, child, true));
        list.append(sub);
      }
    }
    tile.append(list);
    tile.append(addForm("Neue Aufgabe", (text) =>
      run("POST", "todos", { areaId: area.id, title: text })));
    return tile;
  }

  function render(board) {
    boardEl.replaceChildren();
    for (const area of board.areas) boardEl.append(areaTile(area));
    const fresh = el("section", "area new");
    fresh.append(addForm("Neuer Bereich", (name) => run("POST", "areas", { name })));
    boardEl.append(fresh);

    stamp.textContent = "Erledigtes bleibt 3 Tage sichtbar";
    archive.hidden = !(board.hiddenDoneCount > 0 || showAll);
    archive.replaceChildren();
    if (!archive.hidden) {
      const b = el("button", null, showAll
        ? "Ältere erledigte wieder ausblenden"
        : board.hiddenDoneCount + " ältere erledigte anzeigen");
      b.addEventListener("click", () => { showAll = !showAll; load(); });
      archive.append(b);
    }
  }

  load();
})();
