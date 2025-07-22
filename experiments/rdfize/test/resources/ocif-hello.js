const canvas = {
  ocif: "https://canvasprotocol.org/ocif/v0.5",
  nodes: [
    {
      id: "berlin-node",
      position: [
        100,
        100
      ],
      size: [
        100,
        50
      ],
      resource: "berlin-res",
      /* a green rect with a 3 pixel wide black border line */
      data: [
        {
          type: "@ocif/node/rect",
          strokeWidth: 3,
          strokeColor: "#000000",
          fillColor: "#00FF00",
        },
      ],
    },
    {
      id: "germany-node",
      position: [
        300,
        100
      ],
      /* slightly bigger than Berlin */
      size: [
        100,
        60
      ],
      resource: "germany-res",
      /* a white rect with a 5 pixel wide red border line */
      data: [
        {
          type: "@ocif/node/oval",
          strokeWidth: 5,
          strokeColor: "#FF0000",
          fillColor: "#FFFFFF",
        },
      ],
    },
    {
      id: "arrow-1",
      data: [
        {
          type: "@ocif/node/arrow",
          strokeColor: "#000000",
          /* right side of Berlin */
          start: [
            200,
            125
          ],
          /* center of Germany */
          end: [
            350,
            130
          ],
          startMarker: "none",
          endMarker: "arrowhead",
          /* link to relation which is shown by this arrow */
          relation: "relation-1",
        },
      ],
    },
  ],
  relations: [
    {
      id: "relation-1",
      data: [
        {
          type: "@ocif/rel/edge",
          start: "berlin-node",
          end: "germany-node",
          /* WikiData 'is capital of'.
           We could also omit this or just put the string 'is capital of' here. */
          rel: "https://www.wikidata.org/wiki/Property:P1376",
          /* link back to the visual node representing this relation */
          node: "arrow-1",
        },
      ],
    },
  ],
  resources: [
    {
      id: "berlin-res",
      representations: [
        {
          "mimeType": "text/plain", content: "Berlin"
        }
      ],
    },
    {
      id: "germany-res",
      representations: [
        {
          "mimeType": "text/plain", content: "Germany 🇩🇪"
        }
      ],
    },
  ],
}

await Bun.write("hello.json", JSON.stringify(canvas, null, 2))
