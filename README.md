# Weasel Tracker

A RuneLite plugin for Hunter tracking with a noose wand, built for Feldip weasels.

![Highlighted bush at the end of a weasel track](docs/screenshot.png)

## Features

- **Hiding spot highlight**: after inspecting a burrow, the bush at the end of the revealed track is outlined.
  - With a ring of pursuit, the whole trail is revealed and the bush the creature is hiding in is highlighted.
  - Without one, the next bush or plant to search is highlighted as the trail is uncovered.
- **Ring of pursuit charges**: an infobox shows the remaining charges while the ring is worn.

## Configuration

| Option | Description |
| --- | --- |
| Highlight color | Outline color for the hiding spot |
| Outline width | Thickness of the outline |
| Fill clickbox | Shade the hiding spot's clickbox |
| Ring of pursuit charges | Show the charges infobox |
| Highlight last track | Also mark the final tile of the trail |

## Notes

Ring charges are read from game messages. If the infobox shows `?`, use **Check** on the ring once.
